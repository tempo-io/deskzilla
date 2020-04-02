package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.dynaforms.FaceletRegistry;
import com.almworks.api.dynaforms.facelets.ShortTextFacelet;
import com.almworks.api.engine.*;
import com.almworks.api.gui.MainMenu;
import com.almworks.api.http.HttpClientProvider;
import com.almworks.api.http.HttpLoaderFactory;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.search.TextSearch;
import com.almworks.bugzilla.gui.attachments.BugzillaAttachScreenshotAction;
import com.almworks.bugzilla.integration.BugzillaEnv;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.datalink.HoursWorkedLink;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.items.api.Database;
import com.almworks.items.util.DBNamespace;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.platform.DiagnosticRecorder;
import com.almworks.spi.provider.AbstractItemProvider;
import com.almworks.spi.provider.wizard.ConnectionWizard;
import com.almworks.util.*;
import com.almworks.util.config.*;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.NoSameTaskExecutor;
import com.almworks.util.i18n.*;
import com.almworks.util.images.Icons;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.actions.ActionRegistry;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;
import org.picocontainer.defaults.PicoInvocationTargetInitializationException;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.almworks.bugzilla.integration.BugzillaAttribute.SHORT_DESCRIPTION;

/**
 * @author : Dyoma
 */
public class BugzillaProvider extends AbstractItemProvider implements Startable {
  public static final DBNamespace NS = DBNamespace.moduleNs("com.almworks.bugzilla");

  private final MutableComponentContainer mySubcontainer;
  private final CommonMetadata myCommonMetadata;
  private final FaceletRegistry myFaceletRegistry;
  private final Lifecycle myLife = new Lifecycle();
  private final ExecutorService myViewSynchronizerRunner = NoSameTaskExecutor.newCachedThreadPool("bzViewSync");

  // TagsComponent should start first
  public BugzillaProvider(ComponentContainer container, FaceletRegistry faceletRegistry, Configuration config)
  {
    super(config.getOrCreateSubset("connections"));
    myFaceletRegistry = faceletRegistry;
    mySubcontainer = container.createSubcontainer("bz");
    mySubcontainer.registerActor(ROLE, this);
    myCommonMetadata = new CommonMetadata(mySubcontainer, config.getOrCreateSubset("md"));
    mySubcontainer.registerActor(CommonMetadata.ROLE, myCommonMetadata);
    Local.getBook()
      .installProvider(
        new ResourceBundleTextProvider("com.almworks.rc.bugzilla.Bugzilla", LocalTextProvider.Weight.DEFAULT,
          getClass().getClassLoader()));
  }

  public void start() {
    Log.debug("Starting Bugzilla Provider");
    WorkArea workArea = mySubcontainer.getActor(WorkArea.APPLICATION_WORK_AREA);
    BugzillaUtil.setupLocalization(workArea);
    super.start();
    registerMergers();
    registerTriggers();
    BugzillaEnv.setupBugzillaEnv(workArea);
    BugzillaEnv.setupRecorder(mySubcontainer.getActor(DiagnosticRecorder.ROLE));
    TextSearch textSearch = mySubcontainer.getActor(TextSearch.ROLE);
    if (textSearch != null) {
      textSearch.addTextSearchType(Lifespan.FOREVER, mySubcontainer.instantiate(SearchByBugIds.class));
    }
    ActionRegistry actionRegistry = Context.require(ActionRegistry.class);
    actionRegistry.registerAction(MainMenu.Tools.SCREENSHOT, BugzillaAttachScreenshotAction.INSTANCE);
  }

  private void registerMergers() {
    MergeOperationsManager mm = myCommonMetadata.getActor(MergeOperationsManager.ROLE);
    Flags.registerMergers(mm);
    Bug.registerMergers(mm);
    CommentsLink.registerMergers(mm);
    AttachmentsLink.registerMergers(mm);
    CustomField.registerCustomFieldMergers(mm);
  }

  private void registerTriggers() {
    final Database db = mySubcontainer.getActor(Database.ROLE);
    Flags.registerTrigger(db);
    HoursWorkedLink.registerTrigger(db);
  }

  public void stop() {
    myLife.dispose();
  }

  protected void longStart() {
    myCommonMetadata.start();
    CommonMetadata.registerConstraintDescriptors(myCommonMetadata);
    myCommonMetadata.initMetaInfo();
    registerFacelets();
  }

  private void registerFacelets() {
    Lifespan life = myLife.lifespan();
    myFaceletRegistry.registerFacelet(life,
      new ShortTextFacelet("crud.bugzilla.summary", "summary", BugzillaUtil.getDisplayableFieldName(SHORT_DESCRIPTION), BugzillaKeys.summary, Bug.attrSummary));
  }

  public Connection createConnection(String connectionID, ReadonlyConfiguration configuration, boolean isNew)
    throws ConfigurationException
  {
    assert connectionID != null;
    MutableComponentContainer container = mySubcontainer.createSubcontainer(connectionID);
    container.registerActor(MutableComponentContainer.ROLE, container);
    container.registerActor(Role.role("providerID"), connectionID);
    container.registerActor(Role.role("readonlyConfiguration"), configuration);
    container.registerActor(Role.role("isNew"), isNew);
    try {
      //BugzillaConnection connection = container.instantiate(BugzillaConnection.class, connectionID);
      return container.instantiate(BugzillaConnection.class);
    } catch (PicoInvocationTargetInitializationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ConfigurationException)
        throw (ConfigurationException) cause;
      throw e;
    }
  }

  public String getProviderID() {
    return "bz";
  }

  public Icon getIcon() {
    return Icons.PROVIDER_ICON_BUGZILLA;
  }

  public String getProviderName() {
    return "Bugzilla";
  }

  @Override
  protected ConnectionWizard createNewConnectionWizard() {
    return BugzillaConnectionWizard.forNewConnection(mySubcontainer);
  }

  @Override
  protected ConnectionWizard createEditConnectionWizard(Connection connection) {
    if(connection instanceof BugzillaConnection) {
      return BugzillaConnectionWizard.forEditing(mySubcontainer, (BugzillaConnection)connection);
    }

    assert false : connection;
    return null;
  }

  @Override
  public PrimaryItemStructure getPrimaryStructure() {
    return Bug.STRUCTURE;
  }

  public CommonMetadata getCommonMetadata() {
    return myCommonMetadata;
  }

  public boolean isItemUrl(String url) {
    String baseUrl = BugzillaIntegration.getBaseUrlFromArtifactUrl(url);
    return baseUrl != null;
  }

  @Override
  @Nullable
  public String getDisplayableItemIdFromUrl(String url) {
    Integer id = BugzillaIntegration.getBugIdFromUrl(url);
    return id != null ? "#" + id : null;
  }

  @Nullable
  public Configuration createDefaultConfiguration(String itemUrl) {
    String baseUrl = BugzillaIntegration.getBaseUrlFromArtifactUrl(itemUrl);
    if (baseUrl == null || baseUrl.length() == 0)
      return null;
    Configuration config = MapMedium.createConfig();
    config.setSetting(OurConfiguration.BASE_URL, baseUrl);
    config.setSetting(OurConfiguration.CHARSET, "UTF-8");
    config.setSetting(OurConfiguration.TIMEZONE, TimeZone.getDefault().getID());
    config.setSetting(OurConfiguration.IS_ANONYMOUS_ACCESS, true);
    config.setSetting(OurConfiguration.IS_CHARSET_SPECIFIED, true);
    config.setSetting(CommonConfigurationConstants.CONNECTION_NAME, fetchHostname(baseUrl) + " (auto)");
    return config;
  }

  private String fetchHostname(String baseUrl) {
    if (baseUrl.startsWith("http://"))
      baseUrl = baseUrl.substring(7);
    else if (baseUrl.startsWith("https://"))
      baseUrl = baseUrl.substring(8);
    int k = baseUrl.indexOf('/');
    if (k >= 0)
      baseUrl = baseUrl.substring(0, k);
    baseUrl = baseUrl.trim();
    if (baseUrl.length() == 0)
      return "New Connection";
    else
      return baseUrl;
  }

  public ProviderActivationAgent createActivationAgent() {
    return new ProviderActivationAgent() {
      public String getActivationIntroText(int siteCount) {
        return "Please enter the addresses of your Bugzilla " + English.getSingularOrPlural("server", siteCount) + ":";
      }

      @Nullable
      public URL normalizeUrl(String url) {
        try {
          String norm = BugzillaIntegration.normalizeURL(url);
          return new URL(norm);
        } catch (MalformedURLException e) {
          Log.debug(e);
          return null;
        }
      }

      @Nullable
      public String isUrlAccessible(String useUrl, ScalarModel<Boolean> cancelFlag) {
        try {
          BugzillaIntegration checker = new BugzillaIntegration(useUrl, Context.require(HttpClientProvider.class),
            Context.require(HttpLoaderFactory.class), null, null, null, null);
          checker.setCancelFlag(Lifespan.FOREVER, cancelFlag);
          checker.checkConnection();
          return null;
        } catch (MalformedURLException e) {
          return e.getMessage();
        } catch (ConnectorException e) {
          return e.getShortDescription();
        }
      }
    };
  }

  public ExecutorService getViewSynchronizerRunner() {
    return myViewSynchronizerRunner;
  }

  static {
    GlobalLogPrivacy.installPolizei(new BugzillaPasswordPolizei());
  }

  private static class BugzillaPasswordPolizei implements LogPrivacyPolizei {
    private final Pattern myPasswordPattern = Pattern.compile("(.*)(_password\\s*=\\s*)([^\\&\\#]+)(.*)");
    private final Pattern myEmailPattern = Pattern.compile("(.*)(_login\\s*=\\s*)([^\\&\\#\\@\\%]+)([\\@\\%].*)");

    @NotNull
    public String examine(String message) {
      if (message == null) {
        assert false;
        return message;
      }
      int k;
      k = message.indexOf("Bugzilla_");
      if (k < 0)
        k = message.indexOf("LDAP_");
      if (k < 0)
        return message;
      k = message.indexOf("Bugzilla_password");
      if (k < 0)
        k = message.indexOf("LDAP_password");

      if (k >= 0) {
        Matcher matcher = myPasswordPattern.matcher(message);
        if (matcher.matches())
          message = matcher.replaceAll("$1$2********$4");
      }
      k = message.indexOf("Bugzilla_login");
      if (k < 0)
        k = message.indexOf("LDAP_login");
      if (k >= 0) {
        Matcher matcher = myEmailPattern.matcher(message);
        if (matcher.matches())
          message = matcher.replaceAll("$1$2********$4");
      }
      return message;
    }
  }
}