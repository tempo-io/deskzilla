package com.almworks.database;

import com.almworks.api.database.*;

public class RevisionOrderTests extends WorkspaceFixture {
  public void testLocalArtifact() {
    Artifact a = createObjectAndSetValue(myAttributeOne, "x");
    changeArtifact(a, RevisionAccess.ACCESS_DEFAULT, myAttributeTwo, "y");
    changeArtifact(a, RevisionAccess.ACCESS_DEFAULT, myAttributeOne, "z");

    checkChain(a, RevisionAccess.ACCESS_DEFAULT);
  }

  private void checkChain(Artifact artifact, RevisionAccess access) {
    Revision revision = artifact.getLastRevision(access);
    long lastOrder = Long.MAX_VALUE;
    while (revision != null) {
      long order = revision.getOrder();
      assertTrue(revision.toString(), order < lastOrder);
      lastOrder = order;
      revision = revision.getPrevRevision();
    }
  }

  public void testRCBChainsWithoutClosure() {
    Artifact a = createRCB("x");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "z");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "c");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "d");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "e");

    checkChain(a, RevisionAccess.ACCESS_LOCAL);
    checkChain(a, RevisionAccess.ACCESS_MAINCHAIN);
  }

  public void testRCBChainsWithClosure() {
    Artifact a = createRCB("x");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "z");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "c");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeOne, "e");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "d");
    RCBArtifact rcb = a.getRCBExtension(true);
    rcb.closeLocalChain(myWorkspace);

    checkChain(a, RevisionAccess.ACCESS_LOCAL);
    checkChain(a, RevisionAccess.ACCESS_MAINCHAIN);
  }

  public void testRCBChainMultipleClosure() {
    Artifact a = createRCB("x");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "z");
    changeArtifact(a, RevisionAccess.ACCESS_MAINCHAIN, myAttributeTwo, "zz");

    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "1");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "2");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeTwo, "3");
    a.getRCBExtension(true).closeLocalChain(myWorkspace);

    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "111");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "222");
    changeArtifact(a, RevisionAccess.ACCESS_LOCAL, myAttributeOne, "333");
    a.getRCBExtension(true).closeLocalChain(myWorkspace);

    checkChain(a, RevisionAccess.ACCESS_LOCAL);
    checkChain(a, RevisionAccess.ACCESS_MAINCHAIN);
  }
}
