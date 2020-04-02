package com.almworks.database;

import com.almworks.api.database.RevisionAccess;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MainChainTest extends RCBRevisionChainFixture {
  protected RevisionAccess getAccess() {
    return RevisionAccess.ACCESS_MAINCHAIN;
  }
}
