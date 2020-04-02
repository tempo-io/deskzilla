package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ConcurrentTransactionsTests extends WorkspaceFixture {
  public void testParallelChange() {
    Artifact artifact = createObjectAndSetValue(myAttributeOne, "X");
    paraChange(artifact, myAttributeOne, RevisionAccess.ACCESS_DEFAULT, "Y", RevisionAccess.ACCESS_DEFAULT, "Z", true);
  }

  public void testParallelChangeRCB() {
    paraChange(createRCB("X"), myAttributeOne, RevisionAccess.ACCESS_MAINCHAIN, "Y", RevisionAccess.ACCESS_MAINCHAIN, "Z", true);
    paraChange(createRCB("X"), myAttributeOne, RevisionAccess.ACCESS_LOCAL, "Y", RevisionAccess.ACCESS_LOCAL, "Z", true);
    paraChange(createRCB("X"), myAttributeOne, RevisionAccess.ACCESS_LOCAL, "Y", RevisionAccess.ACCESS_MAINCHAIN, "Z", false);
  }

  private void paraChange(Artifact artifact, Attribute attribute, RevisionAccess strategy1, String value1,
    RevisionAccess strategy2, String value2, boolean mustFail) {
    Transaction t1 = createTransaction();
    final Transaction t2 = createTransaction();

    t1.changeArtifact(artifact, strategy1).setValue(attribute, value1);
    t2.changeArtifact(artifact, strategy2).setValue(attribute, value2);

    t1.commitUnsafe();
    if (mustFail) {
      mustThrow(UnsafeCollisionException.class, new RunnableE() {
        public void run() {
          t2.commitUnsafe();
        }
      });
    } else {
      t2.commitUnsafe();
    }
  }
}
