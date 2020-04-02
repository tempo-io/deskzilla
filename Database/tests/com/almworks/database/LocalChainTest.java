package com.almworks.database;

import com.almworks.api.database.RevisionAccess;

/**
 * :todoc:
 *
 * @author sereda
 */
public class LocalChainTest extends RCBRevisionChainFixture {
  protected RevisionAccess getAccess() {
    return RevisionAccess.ACCESS_LOCAL;
  }

  public void testNavigation() throws Exception {
    super.testNavigation();
  }
}
