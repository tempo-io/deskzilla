package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.install.Setup;
import com.almworks.api.install.TrackerProperties;
import com.almworks.api.universe.Universe;
import com.almworks.misc.TestWorkArea;
import com.almworks.universe.MemUniverse;
import com.almworks.universe.optimize.UniverseMemoryOptimizer;
import com.almworks.util.exec.LongEventQueue;
import com.almworks.util.tests.BaseTestCase;

public abstract class WorkspaceFixture extends BaseTestCase implements SystemObjects {
  public static final boolean IGNORE_OLD_DATABASE_TESTS_KNOWN_TO_SOMETIMES_FAIL = true;


  protected Basis myBasis;
  protected Workspace myWorkspace;
  protected Universe myUniverse;
  protected Attribute myAttributeOne;
  protected Attribute myAttributeTwo;
  private Object mySavedCompatibility;
  protected TestWorkArea myWorkArea;

  public WorkspaceFixture() {
    super();
  }

  protected WorkspaceFixture(int timeout) {
    super(timeout);
  }

  protected void setUp() throws Exception {
    super.setUp();
    Setup.cleanupForTestCase();
    UniverseMemoryOptimizer.staticCleanup();
    WorkspaceStatic.cleanup();
    mySavedCompatibility = System.getProperties().put(TrackerProperties.COMPATIBILITY_LEVEL, "1000000");
    myUniverse = new MemUniverse();
    LongEventQueue.installToContext();
    myBasis = createBasis();
    myBasis.start();
    myWorkArea = new TestWorkArea();
    myWorkspace = new WorkspaceImpl(myBasis);
    myWorkspace.repair();
    myWorkspace.open();
    myAttributeOne = createAttribute("one");
    myAttributeTwo = createAttribute("two");
  }

  protected Basis createBasis() {
    return new Basis(myUniverse, ConsistencyWrapper.FAKE);
  }

  protected void tearDown() throws Exception {
    myAttributeTwo = null;
    myAttributeOne = null;
    myWorkspace = null;
    myWorkArea.cleanUp();
    myWorkArea = null;
    myBasis = null;
    LongEventQueue.removeFromContext();
    myUniverse = null;
    if (mySavedCompatibility == null)
      System.getProperties().remove(TrackerProperties.COMPATIBILITY_LEVEL);
    else
      System.getProperties().put(TrackerProperties.COMPATIBILITY_LEVEL, mySavedCompatibility);
    Setup.cleanupForTestCase();
    UniverseMemoryOptimizer.staticCleanup();
    super.tearDown();
  }

  private Attribute createAttribute(String attributeName) {
    Transaction transaction = createTransaction();
    RevisionCreator creator = transaction.createArtifact(Attribute.class);
    Attribute attribute = creator.getArtifact().getTyped(Attribute.class);
    creator.setValue(attribute.attributeName(), attributeName);
    creator.setValue(SystemObjects.ATTRIBUTE.VALUETYPE, SystemObjects.VALUETYPE.PLAINTEXT);
    commitTransaction(transaction);
    return attribute;
  }

  protected Transaction createTransaction() {
    Transaction t = myWorkspace.beginTransaction();
    assertTrue(t != null);
    assertTrue(t.isWorking());
    assertTrue(!t.isCommitted());
    assertTrue(!t.isRolledBack());
    assertTrue(t.isEmpty());
    return t;
  }

  protected void commitTransaction(Transaction t) {
    t.commitUnsafe();
    assertTrue(!t.isWorking());
    assertTrue(!t.isRolledBack());
    assertTrue(t.isCommitted());
  }

  protected void rollbackTransaction(Transaction t) {
    t.rollback();
    assertTrue(!t.isWorking());
    assertTrue(!t.isCommitted());
    assertTrue(t.isRolledBack());
  }

  protected Revision setObjectValue(Transaction transaction, Artifact object, ArtifactPointer attribute,
    String value) {
    return setObjectValue(transaction, object, attribute, value, RevisionAccess.ACCESS_DEFAULT);
  }

  protected Revision setObjectValue(Artifact object, ArtifactPointer attribute, String value, RevisionAccess access) {
    return setObjectValue(null, object, attribute, value, access);
  }

  protected Revision setObjectValue(Transaction transaction, Artifact object, ArtifactPointer attribute,
    String value, RevisionAccess access) {
    String foo;

    boolean local = transaction == null;
    if (local)
      transaction = createTransaction();
    RevisionCreator creator = transaction.changeArtifact(object, access);
    assertTrue(!creator.isBuilt());
    creator.setValue(attribute, value);
    foo = creator.asRevision().getValue(attribute, String.class);
    assertEquals(value, foo);

    if (local) {
      commitTransaction(transaction);
      foo = object.getLastRevision(access).getValue(attribute, String.class);
      assertTrue(foo.equals(value));
      assertTrue(creator.isBuilt());
      foo = creator.asRevision().getChain().getLastRevision().getValue(attribute, String.class);
      assertTrue(foo.equals(value));
    }
    return creator.asRevision();
  }

  protected Revision setObjectValue(Artifact object, ArtifactPointer attribute, String value) {
    return setObjectValue(null, object, attribute, value);
  }

  protected Artifact createObjectAndSetValue(Attribute attribute, String value) {
    Transaction transaction = createTransaction();
    Revision revision = transaction.createArtifact().asRevision();
    Artifact object = revision.getArtifact();
    setObjectValue(transaction, object, attribute, value);
    transaction.commitUnsafe();
    String foo = revision.getChain().getLastRevision().getValues().get(attribute).getValue(
      String.class);
    assertTrue(foo.equals(value));
    return object;
  }

  protected void unsetObjectValue(ArtifactPointer object, Attribute attribute) {
    Transaction transaction = createTransaction();
    RevisionCreator creator = transaction.changeArtifact(object);
    creator.unsetValue(attribute);
    transaction.commitUnsafe();
    assertTrue(object.getArtifact().getLastRevision().getValues().get(attribute) == null);
  }

  protected void deleteObject(ArtifactPointer object) {
    Transaction transaction = createTransaction();
    RevisionCreator creator = transaction.changeArtifact(object);
    creator.deleteObject();
    transaction.commitUnsafe();
    assertTrue(object.getArtifact().getLastRevision().isDeleted());
  }

  protected Artifact createRCB(String attributeOneValue) {
    Transaction t = createTransaction();
    RevisionCreator creator = t.createArtifact(ArtifactFeature.REMOTE_ARTIFACT);
    if (attributeOneValue != null)
      creator.setValue(myAttributeOne, attributeOneValue);
    t.commitUnsafe();
    // creator is the main branch
    return creator.getArtifact();
  }

  protected RevisionCreator changeArtifact(Artifact artifact, RevisionAccess access, ArtifactPointer attribute,
    Object value) {
    Transaction t = createTransaction();
    RevisionCreator creator = t.changeArtifact(artifact, access);
    creator.setValue(attribute, value);
    t.commitUnsafe();
    return creator;
  }

  protected RevisionCreator reincarnate(RCBArtifact rcb, String attributeOneValue) {
    Transaction transaction = createTransaction();
    RevisionCreator creator = rcb.startReincarnation(transaction);
    creator.setValue(myAttributeOne, attributeOneValue);
    transaction.commitUnsafe();
    return creator;
  }
}
