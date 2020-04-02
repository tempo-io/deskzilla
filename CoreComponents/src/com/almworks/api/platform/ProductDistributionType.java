package com.almworks.api.platform;

/**
 * The distribution type governs licensing policy and allowed capabilities of the application.
 * <p/>
 * It is possible to upgrade distribution type from LIGHT to FULL.
 */
public enum ProductDistributionType {
  /**
   * Full distribution type is used with a commercial license (PRO or TEAM license), or with an evaluation period.
   */
  FULL
}
