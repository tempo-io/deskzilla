package com.almworks.database.objects;

import com.almworks.api.database.Artifact;
import com.almworks.api.database.Revision;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
public class NewPhysicalRevisionChain extends AbstractRevisionChain {
  private final RevisionCreationContext myContext;

  public NewPhysicalRevisionChain(Basis basis, long atomKey, RevisionCreationContext newObjects) {
    super(basis, atomKey);
    myContext = newObjects;
  }

  public Artifact getArtifact() {
    return myContext.getArtifact();
  }

  public RevisionIterator getRevisionIterator() {
    return PhysicalRevisionIterator.INSTANCE;
  }

  public Revision getFirstRevision() {
    return myContext.getNewRevision();
  }

  public Revision doGetLastRevision() {
    Revision lastRevision = myBasis.ourRevisionMonitor.getOrCalculateLastRevision(myKey);
    if (lastRevision != null)
      return lastRevision;
    else
      return myContext.getNewRevision();
  }

  public boolean containsRevision(Revision revision) {
    if (revision == null)
      return false;
    if (revision.equals(getFirstRevision()))
      return true;
    return super.containsRevision(revision);
  }
}
