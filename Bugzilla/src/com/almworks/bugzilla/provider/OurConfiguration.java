package com.almworks.bugzilla.provider;

import com.almworks.api.engine.CommonConfigurationConstants;
import com.almworks.spi.provider.util.PasswordUtil;
import com.almworks.util.Pair;
import com.almworks.util.config.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.TimeZone;

public class OurConfiguration {
  public interface Settings779 {
    public static final String PRODUCTS_LIST_DELIMITER = "\\s*[\\,\\;]+\\s*";
    public static final String LIMIT_BY_PRODUCT_PRODUCTS = "limitByProductProducts";
  }

  public static final String USER_NAME = "username";
  public static final String PASSWORD = "password";
  public static final String IS_ANONYMOUS_ACCESS = "anonymousAccess";
  public static final String BASE_URL = "baseURL";
  public static final String CHARSET = "charset";
  public static final String IGNORE_PROXY = "ignoreProxy";
  public static final String TIMEZONE = "serverTimezone";

  public static final String IS_LIMIT_BY_PRODUCT = "isLimitByProduct";
  public static final String LIMIT_BY_PRODUCT_PRODUCTS = "limitProduct";
  public static final String IS_CHARSET_SPECIFIED = "isCharsetSpecified";

  public static final String DEPRECATED_IS_SYNC_ALL_BUGS = "isSyncAllBugs";
  public static final String DEPRECATED_IS_SYNC_ONLY_OPEN = "isSyncOnlyOpen";
  public static final String DEPRECATED_IS_SYNC_ONLY_CHANGED_SINCE = "isSyncOnlyChangedSince";
  public static final String DEPRECATED_SYNC_ONLY_CHANGED_SINCE_DATE = "syncOnlyChangedSinceDate";

  // since 1.0 patch 1
  public static final String IS_USING_EMAIL_SUFFIX = "isUsingEmailSuffix";
  public static final String EMAIL_SUFFIX = "emailSuffix";

  private final ReadonlyConfiguration myConfiguration;

  private TimeZone myTimeZone;

  public OurConfiguration(ReadonlyConfiguration configuration) {
    if (configuration == null)
      throw new NullPointerException("configuration");
    myConfiguration = ConfigurationUtil.copy(configuration);
  }

  public String getPassword() {
    return PasswordUtil.getPassword(myConfiguration);
  }

  public String getUsername() {
    return myConfiguration.getSetting(USER_NAME, "").trim();
  }

  public String getCharset() {
    return myConfiguration.getSetting(CHARSET, "UTF-8");
  }

  public synchronized TimeZone getTimeZone() {
    if (myTimeZone == null) {
      String setting = myConfiguration.getSetting(TIMEZONE, null);
      if (setting != null) {
        myTimeZone = TimeZone.getTimeZone(setting);
        if (myTimeZone != null && "GMT".equals(myTimeZone.getID()) && !Util.upper(setting).startsWith("GMT")) {
          // invalid name of timezone
          myTimeZone = null;
        }
      }
      if (myTimeZone == null) {
        myTimeZone = TimeZone.getDefault();
      }
    }
    return myTimeZone == null ? TimeZone.getDefault() : (TimeZone) myTimeZone.clone();
  }

  public boolean isCharsetSpecified() {
    return getBooleanSetting(IS_CHARSET_SPECIFIED);
  }

  public boolean isIgnoreProxy() {
    return getBooleanSetting(IGNORE_PROXY);
  }

  public boolean isAnonymousAccessSetting() {
    return getBooleanSetting(IS_ANONYMOUS_ACCESS);
  }

  public boolean isAnonymousAccess() {
    return isAnonymousAccessSetting() || getUsername().isEmpty();
  }

  public boolean isUsingEmailSuffix() {
    return isUsingEmailSuffixSetting() && getEmailSuffix().length() > 0;
  }

  public String getEmailSuffix() {
    String undecorated = myConfiguration.getSetting(EMAIL_SUFFIX, "").trim();
    return !undecorated.isEmpty() && !undecorated.startsWith("@") ? ("@" + undecorated) : undecorated; 
  }

  public boolean isUsingEmailSuffixSetting() {
    return getBooleanSetting(IS_USING_EMAIL_SUFFIX);
  }

  @Nullable
  public String getEmailSuffixIfUsing() {
    return isUsingEmailSuffix() ? getEmailSuffix() : null;
  }

  @Nullable
  public String getUserFullEmail() {
    if(isAnonymousAccess()) {
      return null;
    }
    if(isUsingEmailSuffix()) {
      return getUsername() + getEmailSuffix();
    }
    return getUsername();
  }

  /**
   * @return Pair: first is username (always not <code>null</code>), second is <code>null</code> for "anonymous mode",
   *         <code>Boolean.FALSE</code> for empty password and <code>Boolean.TRUE</code> for non-empty password
   */
  public Pair<String, Boolean> getCredentialsInfo() {
    if (isAnonymousAccessSetting())
      return Pair.create(getUsername(), null);
    return Pair.create(getUsername(), Boolean.valueOf(getPassword().length() > 0));
  }

  public String getBaseURL() throws ConfigurationException {
    return myConfiguration.getMandatorySetting(BASE_URL);
  }

  public boolean isLimitByProduct() {
    return getBooleanSetting(IS_LIMIT_BY_PRODUCT);
  }

  public String getConnectionName() {
    return myConfiguration.getSetting(CommonConfigurationConstants.CONNECTION_NAME, "unnamed");
  }

  /**
   * @return limiting product names if limitation is configured. Empty array means no limitation is configured.
   */
  @NotNull
  public String[] getLimitingProducts() {
    return isLimitByProduct() ? getLimitingProducts(myConfiguration) : Const.EMPTY_STRINGS;
  }

  /**
   * Extracts raw limiting value from configuration.
   * @return raw value entred by user and stored in configuration. This value may be inapplicable if the limitByProducts
   * flag isn't set.
   */
  @NotNull
  public static String[] getLimitingProducts(ReadonlyConfiguration configuration) {
    List<String> products = configuration.getAllSettings(LIMIT_BY_PRODUCT_PRODUCTS);
    if (products.size() > 0) {
      List<String> checkedProducts = null;
      for (int i = 0; i < products.size(); i++) {
        String product = getValidProduct(products.get(i));
        if (product != null) {
          if (checkedProducts == null)
            checkedProducts = Collections15.arrayList();
          checkedProducts.add(product);
        }
      }
      return checkedProducts == null ? Const.EMPTY_STRINGS : checkedProducts.toArray(new String[checkedProducts.size()]);
    } else {
      return getLimitingProducts779(configuration);
    }
  }

  private static String getValidProduct(String product) {
    if (product == null)
      return null;
    product = product.trim();
    return product.length() > 0 ? product : null;
  }

  /**
   * Setting in 779 and earlier format
   */
  private static String[] getLimitingProducts779(ReadonlyConfiguration configuration) {
    String setting = configuration.getSetting(Settings779.LIMIT_BY_PRODUCT_PRODUCTS, "").trim();
    if (setting.length() == 0)
      return Const.EMPTY_STRINGS;
    String[] products = setting.split(Settings779.PRODUCTS_LIST_DELIMITER);
    return products != null ? products : Const.EMPTY_STRINGS;
  }


  public ReadonlyConfiguration getConfiguration() {
    return myConfiguration;
  }

  private boolean getBooleanSetting(String setting) {
    return myConfiguration.getSetting(setting, "").trim().equalsIgnoreCase("TRUE");
  }
}

