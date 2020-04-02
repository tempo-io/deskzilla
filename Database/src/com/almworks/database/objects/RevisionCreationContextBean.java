package com.almworks.database.objects;

import com.almworks.api.database.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RevisionCreationContextBean implements RevisionCreationContext {
  private Artifact myArtifact;
  private AbstractRevisionChain myRevisionChain;
  private RevisionCreator myRevisionCreator;

  public Artifact getArtifact() {
    Artifact artifact = myArtifact;
    if (artifact != null)
      return artifact;
    synchronized (this) {
      assert myArtifact != null;
      return myArtifact;
    }
  }

  public synchronized void setArtifact(Artifact artifact) {
    assert myArtifact == null;
    myArtifact = artifact;
  }

  public synchronized Revision getNewRevision() {
    assert myRevisionCreator != null;
    return myRevisionCreator == null ? null : myRevisionCreator.asRevision();
  }

  public RevisionIterator getRevisionIterator() {
    return myRevisionChain.getRevisionIterator();
  }

  public synchronized RevisionChain getRevisionChain() {
    assert myRevisionChain != null;
    return myRevisionChain;
  }

  public synchronized void setRevisionChain(RevisionChain revisionChain) {
    assert myRevisionChain == null;
    // kludge
    if (!(revisionChain instanceof AbstractRevisionChain))
      throw new IllegalArgumentException(revisionChain.toString());
    myRevisionChain = (AbstractRevisionChain) revisionChain;
  }

  public synchronized void setRevisionCreator(RevisionCreator revisionCreator) {
    assert myRevisionCreator == null;
    myRevisionCreator = revisionCreator;
  }
}
