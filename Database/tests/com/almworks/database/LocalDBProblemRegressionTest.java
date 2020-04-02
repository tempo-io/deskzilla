package com.almworks.database;

import com.almworks.api.database.*;

// see PLO-455
public class LocalDBProblemRegressionTest extends WorkspaceFixture {
  /**
   * The problem is caused by artifact pointers lingering in memory cache while they are effectively invalid
   * after 
   */
  public void testChangingEtherealArtifact() {
    Artifact a = createObjectAndSetValue(myAttributeOne, "X");

    Transaction t1 = createTransaction();
    RevisionCreator c1 = t1.changeArtifact(a);
    c1.setValue(myAttributeOne, "Z");

    RevisionCreator ethereal = t1.createArtifact();
    Artifact FAIL = ethereal.getArtifact();

    Transaction t2 = createTransaction();
    RevisionCreator c2 = t2.changeArtifact(a);
    c2.setValue(myAttributeOne, "Y");
    t2.commitUnsafe();

    try {
      t1.commit();
      fail("successful conflicting commit");
    } catch (CollisionException e) {
      // ok!
    }

    Transaction t3 = createTransaction();
    try {
      RevisionCreator f = t3.changeArtifact(FAIL);
    } catch (RuntimeDatabaseInconsistentException e) {
      // ok - this is not fixable
      System.out.println("DB problem confirmed: " + e);
    }
  }
}
