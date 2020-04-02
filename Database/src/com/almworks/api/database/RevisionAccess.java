package com.almworks.api.database;

import com.almworks.util.Enumerable;

/**
 * :todoc:
 *
 * @author sereda
 */
public final class RevisionAccess extends Enumerable {
  /**
   * Use default access for this artifact. Defined for all artifacts.
   */
  public static final RevisionAccess ACCESS_DEFAULT = new RevisionAccess("ACCESS_DEFAULT");

  /**
   * For multi-chain only: use main chain.
   */
  public static final RevisionAccess ACCESS_MAINCHAIN = new RevisionAccess("ACCESS_MAINCHAIN");

  /**
   * For multi-chain only: use main chains with local changes.
   */
  public static final RevisionAccess ACCESS_LOCAL = new RevisionAccess("ACCESS_LOCAL");

  public static final RevisionAccess[] ALL = {ACCESS_DEFAULT, ACCESS_LOCAL, ACCESS_MAINCHAIN};

  private RevisionAccess(String name) {
    super(name);
  }
}
