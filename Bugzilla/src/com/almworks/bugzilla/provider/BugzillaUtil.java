package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.connector.ConnectorStateStorage;
import com.almworks.api.http.*;
import com.almworks.api.install.TrackerProperties;
import com.almworks.api.misc.WorkArea;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.items.api.DBAttribute;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.Env;
import com.almworks.util.commons.Procedure;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.i18n.*;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.Const;
import org.almworks.util.StringUtil;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.io.File;
import java.net.MalformedURLException;
import java.util.TimeZone;

/**
 * @author dyoma
 */
public class BugzillaUtil {
  public static final String TERMS_FILE = "bugzilla-terms.properties";
  public static final String BUGZILLA_PROPERTIES_PREFIX = "bz.";

  /**
   * @param changedSince filter out bugs changed after given time. Negative value means ignore modification time.
   */
  public static void addConfigurationLimits(QueryURLBuilder queryBuilder, OurConfiguration config, long changedSince) {
//    queryBuilder.setOrderByModificationDate(desc);
    if (config.isLimitByProduct())
      queryBuilder.addProductCondition(config.getLimitingProducts());
    if (changedSince > 0)
      queryBuilder.addChangeDateCondition(changedSince);
  }

  public static void setOverrideCharset(BugzillaIntegration integration, String charset) {
    String forceCharset = Env.getString(TrackerProperties.FORCE_CHARSET);
    if (charset != null)
      integration.setOverrideCharset(charset);
    else
      integration.setOverrideCharset(forceCharset);
  }

  static BugzillaIntegration createIntegration(HttpLoaderFactory httpLoaderFactory,
    HttpClientProvider httpClientProvider, String baseURL, boolean anonymous, String username, String password,
    FeedbackHandler feedbackHandler, ScalarModel<Boolean> cancelFlag, String charset, BugzillaAccountNameSink sink,
    boolean noProxy, @Nullable TimeZone defaultTimezone, @Nullable ConnectorStateStorage stateStorage,
    @Nullable String emailSuffix, @Nullable String bzVersion, @Nullable Procedure<String> versionSink)
    throws MalformedURLException
  {
    BugzillaIntegration integration = new BugzillaIntegration(baseURL, httpClientProvider, httpLoaderFactory,
      defaultTimezone, BugzillaProvider.getUserAgent(), emailSuffix, bzVersion);
    integration.setVersionSink(versionSink);
    if (!anonymous)
      integration.setCredentials(username, password, sink);
    if (feedbackHandler != null)
      integration.setFeedbackHandler(feedbackHandler);
    if (cancelFlag != null)
      integration.setCancelFlag(Lifespan.FOREVER, cancelFlag);
    if (noProxy)
      integration.setNoProxy();
    setOverrideCharset(integration, charset);
    integration.setStateStorage(stateStorage);
    return integration;
  }

  public static QueryURLBuilder buildRemoteQuery(BooleanChart queryParam, BugzillaContext context)
    throws ConnectionNotConfiguredException
  {
    BugzillaIntegration integration = context.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD);
    QueryURLBuilder queryBuilder = integration.getURLQueryBuilder();
    OurConfiguration configuration = context.getConfiguration().getValue();
    if (configuration == null)
      throw new ConnectionNotConfiguredException();
    addConfigurationLimits(queryBuilder, configuration, 0);
    queryBuilder.addBooleanChart(queryParam);
    return queryBuilder;
  }

  static String buildRemoteQueryUrl(@Nullable BooleanChart queryParam, BugzillaContext context, boolean absolute)
    throws ConnectionNotConfiguredException
  {
    QueryURLBuilder queryBuilder = buildRemoteQuery(queryParam, context);
    String url = queryBuilder.getURL();
    if (absolute) {
      try {
        String base = BugzillaIntegration.normalizeURL(context.getConfiguration().getValue().getBaseURL());
        url = base + url;
      } catch (MalformedURLException e) {
        throw new ConnectionNotConfiguredException();
      } catch (ConfigurationException e) {
        throw new ConnectionNotConfiguredException();
      }
    }
    return url;
  }

  public static String getDisplayableFieldName(BugzillaAttribute bzAttribute) {
    String name = bzAttribute.getName();
    return getDisplayableFieldName(name);
  }

  public static String getDisplayableFieldName(String name) {
    String compacted = StringUtil.removeWhitespaces(name);
    return Local.text("bz.field." + compacted, name);
  }

  public static void setupLocalization(WorkArea workArea) {
    setupTerms(workArea);
  }

  private static void setupTerms(WorkArea workArea) {
    File file = workArea.getEtcFile(TERMS_FILE);
    if (file != null) {
      LocalTextProvider provider =
        new PropertiesBundleTextProvider(file, LocalTextProvider.Weight.USER, null, BUGZILLA_PROPERTIES_PREFIX);
      Local.getBook().installProvider(provider);
    }
  }

  public static boolean similarComments(String commentA, String commentB) {
    return toleratingCommentHash(commentA) == toleratingCommentHash(commentB);
  }

  public static boolean commentDatesMatch(long date, long cdate) {
    return Math.abs(cdate - date) < (Const.MINUTE * 3 / 2);
  }

  public static int toleratingCommentHash(String comment) {
    if (comment == null)
      return 0;
    char[] chars = comment.toCharArray();
    int result = 0;
    int len = chars.length;
    for (int i = 0; i < len; i++) {
      char c = chars[i];
      if (Character.isLetterOrDigit(c)) {
        result = (result << 5) - result + (int) Character.toLowerCase(c);
      }
    }
    return result;
  }

  public static boolean assertScalarValueType(DBAttribute attribute) {
    assert attribute.getComposition() == DBAttribute.ScalarComposition.SCALAR : attribute;
    return true;
  }

  @Nullable
  public static BugzillaContext getContext(ModelMap modelMap) {
    return getContext(LoadedItemServices.VALUE_KEY.getValue(modelMap));
  }

  @Nullable
  public static BugzillaContext getContext(@Nullable LoadedItemServices itemServices) {
    if (itemServices != null) {
      BugzillaConnection connection = itemServices.getConnection(BugzillaConnection.class);
      return connection != null ? connection.getContext() : null;
    }
    assert false;
    return null;
  }

  public static BugzillaContext getContext(ItemWrapper wrapper) {
    return wrapper == null ? null : getContext(wrapper.services());
  }
}