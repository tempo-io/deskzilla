package com.almworks.database;

import com.almworks.api.database.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class AbstractRevisionChainFixture extends WorkspaceFixture {
  public static final String VALUE = "val";
  protected Artifact myArtifact;

  public void testGetPreviousValue() throws Exception {
    Revision first = setObjectValue(myArtifact, myAttributeOne, "x");
    Revision second = setObjectValue(myArtifact, myAttributeTwo, "y");
    assertEquals("x", second.getValue(myAttributeOne, String.class));
  }

  public void testGetUnsetValue() throws Exception {
    Revision object = setObjectValue(myArtifact, myAttributeOne, "x");
    assertTrue(object.getValues().get(myAttributeTwo) == null);
  }

  public void testLastRevisionUpdate() throws Exception {
    RevisionChain chain = myArtifact.getChain(getAccess());
    assertEquals(myArtifact.getFirstRevision(), chain.getLastRevision());
    Revision newRev = setObjectValue(myArtifact, myAttributeOne, "foo");
    assertEquals(newRev, chain.getLastRevision());
    newRev = setObjectValue(myArtifact, myAttributeTwo, "fooz");
    assertEquals(newRev, chain.getLastRevision());
  }

  public void testNavigation() throws Exception {
    Revision r2 = setObjectValue(myArtifact, myAttributeOne, "val2");
    Revision r3 = setObjectValue(myArtifact, myAttributeTwo, "val3");
    Revision r1 = myArtifact.getLastRevision(getAccess()).getChain().getFirstRevision();
    RevisionChain chain = r1.getChain();

    assertEquals(myArtifact, r3.getArtifact());
    assertEquals(myArtifact, r2.getArtifact());

    assertEquals(chain, r1.getChain());
    assertEquals(chain, r2.getChain());
    assertEquals(chain, r3.getChain());

    assertEquals(r3, r1.getChain().getLastRevision());
    assertEquals(r3, r2.getChain().getLastRevision());
    assertEquals(r3, r3.getChain().getLastRevision());

    assertEquals(r2, r3.getPrevRevision());
    assertEquals(r1, r2.getPrevRevision());
  }

  public void testNoChangesTransaction() {
    Revision object1 = setObjectValue(myArtifact, myAttributeOne, "xx");
    WCN wcn1 = object1.getWCN();
    Revision object2 = setObjectValue(myArtifact, myAttributeOne, "xx");
    WCN wcn2 = object2.getWCN();
    assertEquals(wcn1, wcn2);
  }

  public void testSingleRevision() {
    Revision firstRevision = myArtifact.getFirstRevision();
    assertEquals(myArtifact, firstRevision.getArtifact());
    assertTrue(firstRevision.getPrevRevision() == null);
    assertTrue(myArtifact.getChain(getAccess()).getLastRevision().equals(firstRevision));
    assertEquals(myArtifact, myArtifact.getArtifact());
    assertEquals(VALUE, firstRevision.getValue(myAttributeOne, String.class));
  }

  protected abstract Artifact createArtifact();

  protected abstract RevisionAccess getAccess();

  protected Revision setObjectValue(Artifact object, ArtifactPointer attribute, String value) {
    return super.setObjectValue(object, attribute, value, getAccess());
  }

  protected void setUp() throws Exception {
    super.setUp();
    myArtifact = createArtifact();
  }

  protected void tearDown() throws Exception {
    myArtifact = null;
    super.tearDown();
  }
}
