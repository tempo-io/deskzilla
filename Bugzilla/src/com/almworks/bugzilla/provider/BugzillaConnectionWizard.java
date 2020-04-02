package com.almworks.bugzilla.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.spi.provider.NewConnectionSink;
import com.almworks.spi.provider.util.PasswordUtil;
import com.almworks.spi.provider.wizard.*;
import com.almworks.util.Pair;
import com.almworks.util.collections.Convertors;
import com.almworks.util.components.ASeparator;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.InlineLayout;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Connection wizard for Bugzilla.
 */
public class BugzillaConnectionWizard extends ConnectionWizard {
  public static final String AUTH_PAGE_ID = "wizard.newConnection.bugzilla.advAuth";
  public static final String LITE_TEST_PAGE_ID = "wizard.newConnection.bugzilla.liteTest";

  private final OurConfiguration myOldConfig;

  private final UrlPage myUrlPage;
  private final AuthPage myAuthPage;
  private final MainTestPage myMainTestPage;
  private final ProductsPage myProdPage;
  private final NamePage myNamePage;
  private final InitPage myInitPage;
  private final WaitPage myWaitPage;

  private final ConnectionTester myTester;

  private boolean myEmailPageUsed;

  public static BugzillaConnectionWizard forNewConnection(ComponentContainer container) {
    return new BugzillaConnectionWizard("New Bugzilla Connection", container, null);
  }

  public static BugzillaConnectionWizard forEditing(ComponentContainer container, BugzillaConnection connection) {
    return new BugzillaConnectionWizard("Edit Bugzilla Connection", container, connection);
  }

  private BugzillaConnectionWizard(String title, ComponentContainer container, BugzillaConnection connection) {
    super(title, container, connection);

    myOldConfig = getOriginalConfiguration();

    myUrlPage = new UrlPage();
    myAuthPage = new AuthPage();
    myMainTestPage = new MainTestPage();
    myProdPage = new ProductsPage();
    myNamePage = new NamePage();
    myInitPage = new InitPage();
    myWaitPage = new WaitPage();
    initialize(myUrlPage, myAuthPage, myMainTestPage, myProdPage, myNamePage, myInitPage, myWaitPage);

    myTester = new ConnectionTester(container, connection, myMainTestPage);
  }

  private OurConfiguration getOriginalConfiguration() {
    if(myEditing) {
      return new OurConfiguration(myEditedConnection.getConfiguration());
    } else {
      return null;
    }
  }

  @Override
  protected void cancelConnectionTest() {
    myTester.cancelTesting();
  }

  @Override
  protected boolean isIgnoreProxy() {
    return myWizardConfig.getBooleanSetting(OurConfiguration.IGNORE_PROXY, false);
  }

  @Override
  public String getUrl() {
    return myUrlPage.getNormalizedUrl();
  }

  @Override
  public boolean isAnonymous() {
    return myUrlPage.isAnonymous();
  }

  @Override
  public String getUserName() {
    return myUrlPage.getUserName();
  }

  @Override
  protected boolean areCredentialsChanged() {
    if(myEditing) {
      final OurConfiguration newConfig = new OurConfiguration(myWizardConfig);
      return isAnonymityChanged(newConfig) || areCredentialsChanged(newConfig);
    }
    return false;
  }

  private boolean isAnonymityChanged(OurConfiguration newConfig) {
    return myOldConfig.isAnonymousAccess() != newConfig.isAnonymousAccess();
  }

  private boolean areCredentialsChanged(OurConfiguration newConfig) {
    if(!myOldConfig.isAnonymousAccess()) {
      return !Util.equals(myOldConfig.getUsername(), newConfig.getUsername())
        || !Util.equals(myOldConfig.getPassword(), newConfig.getPassword());
    }
    return false;
  }

  @Override
  protected boolean areSelectedUnitsChanged() {
    if(myEditing) {
      final OurConfiguration newConfig = new OurConfiguration(myWizardConfig);
      return !Util.equals(
        Collections15.hashSet(myOldConfig.getLimitingProducts()),
        Collections15.hashSet(newConfig.getLimitingProducts()));
    }
    return false;
  }

  @Override
  protected NewConnectionSink getNewConnectionSink() {
    return myInitPage;
  }

  /**
   * URL and credentials page.
   */
  class UrlPage extends BaseUrlPage<BugzillaConnectionUrlForm> {
    UrlPage() {
      myMoreAction = new AdvancedAction();
    }

    @Override
    protected BugzillaConnectionUrlForm createForm() {
      return new BugzillaConnectionUrlForm();
    }

    @Override @NotNull
    protected Pair<String, String> getInfoAndDescription() {
      String message = null;
      String msgDescr = null;

      return Pair.create(message, msgDescr);
    }

    @Override @NotNull
    protected Pair<String, String> getErrorAndDescription() {
      try {
        doCheckForErrors();
      } catch(CheckFailed failure) {
        return failure.asStringPair();
      }
      return Pair.create(null, null);
    }

    private void doCheckForErrors() throws CheckFailed {
      final String url = getNormalizedUrl();
      checkUrlIsPresent(url);
      checkCredentials();
    }

    private void checkUrlIsPresent(String url) throws CheckFailed {
      if(url == null) {
        throw new CheckFailed("Please enter a valid Bugzilla server URL.");
      }
    }

    private void checkCredentials() throws CheckFailed {
      if(!isAnonymous() && (getUserName().isEmpty() || getPassword().isEmpty())) {
        throw new CheckFailed("Please enter valid login and password or select Anonymous access.");
      }
    }

    @Override
    public void nextInvoked() {
      myEmailPageUsed = !isAnonymous() && !isSuffixSet() && !isEmail(getUserName());
      if(myEmailPageUsed) {
        myNextPageID = AUTH_PAGE_ID;
      } else {
        myNextPageID = TEST_PAGE_ID;
      }
    }

    private boolean isSuffixSet() {
      return myWizardConfig.getBooleanSetting(OurConfiguration.IS_USING_EMAIL_SUFFIX, false)
        && !myWizardConfig.getSetting(OurConfiguration.EMAIL_SUFFIX, "").isEmpty();
    }

    private boolean isEmail(String str) {
      if(str == null || str.isEmpty()) {
        return false;
      }

      final int firstIndex = str.indexOf('@');
      if(firstIndex < 1 || firstIndex >= str.length() - 1) {
        return false;
      }

      final int lastIndex = str.lastIndexOf('@');
      return firstIndex == lastIndex;
    }

    @Override
    protected void saveValuesToConfiguration() {
      saveValuesToConfiguration(myWizardConfig);
    }

    private void saveValuesToConfiguration(Configuration config) {
      config.setSetting(OurConfiguration.BASE_URL, getNormalizedUrl());
      config.setSetting(OurConfiguration.IS_ANONYMOUS_ACCESS, isAnonymous());
      config.setSetting(OurConfiguration.USER_NAME, getUserName());
      PasswordUtil.setPassword(config, getPassword());
    }

    @Override
    public String getNormalizedUrl() {
      try {
        return BugzillaIntegration.normalizeURL(super.getNormalizedUrl());
      } catch(MalformedURLException e) {
        return null;
      }
    }
  }

  /**
   * "Advanced" action for the URL page.
   */
  class AdvancedAction extends BaseAdvancedAction {
    @Override
    public void perform(ActionContext context) throws CantPerformException {
      final DialogBuilder builder = createBuider();
      final ButtonModel ignoreModel = createIgnoreModel();
      final BugzillaConnectionAdvancedForm form = new BugzillaConnectionAdvancedForm();
      createDialogContent(builder, ignoreModel, form);
      loadInitialValues(form);
      addOkListener(builder, ignoreModel, form);
      builder.showWindow();
    }

    private void createDialogContent(
      DialogBuilder builder, ButtonModel ignoreModel, BugzillaConnectionAdvancedForm form)
    {
      final JPanel content = new JPanel(new InlineLayout(InlineLayout.VERTICAL, 5, true));
      content.add(createBugzillaSection(form));
      content.add(Box.createVerticalStrut(10));
      content.add(createProxySection(ignoreModel));
      content.add(Box.createVerticalStrut(10));
      builder.setContent(content);
    }

    private JComponent createBugzillaSection(BugzillaConnectionAdvancedForm form) {
      return ASeparator.wrap("Bugzilla", form.getComponent());
    }

    private void loadInitialValues(BugzillaConnectionAdvancedForm form) {
      form.loadFromConfiguration(myWizardConfig);
    }

    private void addOkListener(
      DialogBuilder builder, final ButtonModel ignoreModel, final BugzillaConnectionAdvancedForm form)
    {
      builder.addOkListener(new AnActionListener() {
        @Override
        public void perform(ActionContext context) throws CantPerformException {
          ok(ignoreModel, form);
        }
      });
    }

    private void ok(ButtonModel ignoreModel, BugzillaConnectionAdvancedForm form) {
      saveIgnoreProxy(ignoreModel.isSelected());
      saveBugzillaSettings(form);
    }

    private void saveIgnoreProxy(boolean ignore) {
      if(ignore) {
        myWizardConfig.setSetting(OurConfiguration.IGNORE_PROXY, true);
      } else {
        myWizardConfig.removeSettings(OurConfiguration.IGNORE_PROXY);
      }
    }

    private void saveBugzillaSettings(BugzillaConnectionAdvancedForm form) {
      form.saveToConfiguration(myWizardConfig);
    }
  }

  /**
   * User e-mail page (for e-mail suffix configuration). 
   */
  class AuthPage extends BasePage {
    private BugzillaConnectionAuthForm myForm;
    private String myStashedSuffix;

    AuthPage() {
      super("Bugzilla authentication", AUTH_PAGE_ID, URL_PAGE_ID, TEST_PAGE_ID);
    }

    @Override
    protected JComponent createContent() {
      if(myForm == null) {
        myForm = new BugzillaConnectionAuthForm();
      }
      return myForm.myWholePanel;
    }

    @Override
    public void aboutToDisplayPage(String prevPageID) {
      loadUsername();
      loadDomain();
    }

    private void loadUsername() {
      myForm.myUsernameLabel.setText(getUserName() + "@");
    }

    private void loadDomain() {
      final OurConfiguration our = new OurConfiguration(myWizardConfig);
      if(our.isUsingEmailSuffix()) {
        final String suffix = our.getEmailSuffix();
        if(suffix.startsWith("@")) {
          myForm.myDomainField.setText(suffix.substring(1));
        }
      } else if(myStashedSuffix != null) {
        myForm.myDomainField.setText(myStashedSuffix);
      }
    }

    @Override
    public void displayingPage(String prevPageID) {
      setInitialFocus();
      setNextEnabled(true);
    }

    private void setInitialFocus() {
      if(myForm.myExternalOption.isSelected()) {
        myForm.myExternalOption.requestFocusInWindow();
      } else if(myForm.mySuffixOption.isSelected()) {
        myForm.myDomainField.requestFocusInWindow();
      } else if(myForm.myNeitherOption.isSelected()) {
        myForm.myNeitherOption.requestFocusInWindow();
      }
    }

    @Override
    public void aboutToHidePage(String nextPageID) {
      super.aboutToHidePage(nextPageID);
      if(myNextPageID.equals(nextPageID)) {
        saveSuffixToConfiguration();
      } else if(myPrevPageID.equals(nextPageID)) {
        stashSuffix();
      }
    }

    private void saveSuffixToConfiguration() {
      final String domain = myForm.myDomainField.getText();
      if(myForm.mySuffixOption.isSelected() && !domain.isEmpty()) {
        myWizardConfig.setSetting(OurConfiguration.IS_USING_EMAIL_SUFFIX, true);
        myWizardConfig.setSetting(OurConfiguration.EMAIL_SUFFIX, "@" + domain);
      }
    }

    private void stashSuffix() {
      final String domain = myForm.myDomainField.getText();
      myStashedSuffix = domain.isEmpty() ? null : domain;
      myWizardConfig.removeSettings(OurConfiguration.IS_USING_EMAIL_SUFFIX);
      myWizardConfig.removeSettings(OurConfiguration.EMAIL_SUFFIX);
    }
  }

  /**
   * Connection test page. 
   */
  class MainTestPage extends BaseTestPage implements ConnectionTester.TestProgressIndicator {
    @Override
    protected void beginConnectionTest() {
      myTester.test(myWizardConfig, false);
    }

    @Override
    public void showMessage(boolean problem, String message, String explanation) {
      showInfo(problem, message, explanation);
    }

    @Override
    public void backInvoked() {
      myPrevPageID = myEmailPageUsed ? AUTH_PAGE_ID : URL_PAGE_ID;
    }
  }

  /**
   * Product selection page.
   */
  class ProductsPage extends BaseUnitsPage<ConnectionTester.Product> {
    @Override
    public void aboutToDisplayPage(String prevPageID) {
      if(TEST_PAGE_ID.equals(prevPageID)) {
        chooseNextPage();
        setAvailableProductsPreservingSelection();
      }
      super.aboutToDisplayPage(prevPageID);
    }

    private void chooseNextPage() {
      myNextPageID = NAME_PAGE_ID;
    }

    private void setAvailableProductsPreservingSelection() {
      getUnitsList().setCollectionModel(myTester.getProductsModel(), true);
    }

    @Override
    protected void loadSelectionFromConfiguration() {
      final OurConfiguration our = new OurConfiguration(myWizardConfig);
      if(our.isLimitByProduct()) {
        final String[] names = our.getLimitingProducts();
        getUnitsList().getCheckedAccessor().setSelected(ConnectionTester.createProducts(names));
      } else {
        getUnitsList().getCheckedAccessor().clearSelection();
      }
    }

    @Override
    protected Pair<String, String> checkForErrors() {
      final Pair<String, String> upstreamError = super.checkForErrors();
      if(upstreamError != null) {
        return upstreamError;
      }
      return checkLiteRestrictions();
    }

    private Pair<String, String> checkLiteRestrictions() {
      return null;
    }

    @Override
    protected void saveSelectionToConfiguration() {
      if(isFiltering()) {
        saveConfigSettings();
      } else {
        removeConfigSettings();
      }
    }

    private void saveConfigSettings() {
      myWizardConfig.setSetting(OurConfiguration.IS_LIMIT_BY_PRODUCT, true);
      myWizardConfig.setSettings(OurConfiguration.LIMIT_BY_PRODUCT_PRODUCTS, getSelectedProductNames());
    }

    public List<String> getSelectedProductNames() {
      return isFiltering()
        ? Convertors.TO_STRING.collectList(getSelectedUnits())
        : Collections15.<String>emptyList();
    }

    private void removeConfigSettings() {
      myWizardConfig.removeSettings(OurConfiguration.IS_LIMIT_BY_PRODUCT);
      myWizardConfig.removeSettings(OurConfiguration.LIMIT_BY_PRODUCT_PRODUCTS);
    }

    @Override
    public void backInvoked() {
      myPrevPageID = myEmailPageUsed ? AUTH_PAGE_ID : URL_PAGE_ID;
    }
  }

  /**
   * Name & Review page.
   */
  class NamePage extends BaseNamePage {
    @Override
    protected List<String> getSelectedUnitNames() {
      return myProdPage.getSelectedProductNames();
    }

    @Override
    protected String suggestConnectionNameByUrl() {
      try {
        return new URL(getUrl()).getHost();
      } catch(MalformedURLException e) {
        return "";
      }
    }
  }
}
