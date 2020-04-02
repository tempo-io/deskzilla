package com.almworks.database.migration.upgrade;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.install.Setup;
import com.almworks.database.*;
import com.almworks.database.migration.MigrationControllerImpl;
import com.almworks.database.migration.MigrationFailure;
import com.almworks.misc.TestWorkArea;
import com.almworks.universe.FileUniverse;
import com.almworks.util.exec.LongEventQueue;
import com.almworks.util.tests.BaseTestCase;

public class ValueMarshallingCompactizationMigrationTestz extends BaseTestCase {
  private TestWorkArea myWorkArea;
  private FileUniverse myUniverse;
  private Basis myBasis;
  private WorkspaceImpl myWorkspace;
  private Attribute myAttributeOne;
  private Attribute myAttributeTwo;

  protected void setUp() throws Exception {
    super.setUp();
    Setup.cleanupForTestCase();
    WorkspaceStatic.cleanup();
    myWorkArea = new TestWorkArea();
    myUniverse = new FileUniverse(myWorkArea);
    myUniverse.start();
    LongEventQueue.installToContext();
    myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis.start();
    myWorkspace = new WorkspaceImpl(myBasis);
    myWorkspace.repair();
    myWorkspace.open();
    myAttributeOne = createAttribute("one");
    myAttributeTwo = createAttribute("two");
    myWorkspace.stop();
    myUniverse.stop();
  }

  private Attribute createAttribute(String attributeName) {
    Transaction transaction = myWorkspace.beginTransaction();
    RevisionCreator creator = transaction.createArtifact(Attribute.class);
    Attribute attribute = creator.getArtifact().getTyped(Attribute.class);
    creator.setValue(attribute.attributeName(), attributeName);
    creator.setValue(SystemObjects.ATTRIBUTE.VALUETYPE, SystemObjects.VALUETYPE.PLAINTEXT);
    transaction.commitUnsafe();
    return attribute;
  }

  protected void tearDown() throws Exception {
    if (myUniverse != null) {
      try {
        myUniverse.stop();
      } catch (Exception e) {
        // ignore
      }
    }
    myWorkspace = null;
    myUniverse = null;
    LongEventQueue.removeFromContext();
    Setup.cleanupForTestCase();
    myBasis = null;
    super.tearDown();
  }

  public void testMigration() throws MigrationFailure {
    MigrationControllerImpl controller = new MigrationControllerImpl();
    ValueMarshallingCompactizationMigration migration =
      new ValueMarshallingCompactizationMigration(myWorkArea, controller);
    migration.migrate();
    myUniverse = new FileUniverse(myWorkArea);
    myUniverse.start();
  }
}
