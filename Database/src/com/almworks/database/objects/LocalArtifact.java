package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.database.*;
import org.almworks.util.Log;

/**
 * :todoc:
 *
 * @author sereda
 */
public class LocalArtifact extends AbstractArtifactImpl {
  private final RevisionChain myChain;

  public LocalArtifact(Basis basis, long key) {
    this(basis, key, basis.getPhysicalChain(key));
  }

  LocalArtifact(Basis basis, long atomKey, RevisionChain chain) {
    super(basis, atomKey);
    assert chain != null;
    myChain = chain;
  }

  public RevisionChain getChain(RevisionAccess strategy) {
    assert strategy == RevisionAccess.ACCESS_DEFAULT;
    if (strategy != RevisionAccess.ACCESS_DEFAULT)
      Log.warn("accessing local artifact with access " + strategy, new Throwable());
    return myChain;
  }

  public boolean isAccessStrategySupported(RevisionAccess strategy) {
    return strategy == RevisionAccess.ACCESS_DEFAULT;
  }

  public boolean containsRevision(Revision revision) {
    return getChain(RevisionAccess.ACCESS_DEFAULT).containsRevision(revision);
  }

  public boolean isValid() {
    return true;
  }

  public static RevisionCreator createNew(Basis basis, TransactionExt transaction) {
    return LocalArtifactLogic.createNew(basis, transaction);
  }

  public static RevisionCreator change(Basis basis, TransactionExt transaction, Artifact artifact,
    RevisionAccess accessStrategy, Revision baseRevision) throws DatabaseInconsistentException
  {

    return LocalArtifactLogic.changeArtifact(basis, transaction, artifact, accessStrategy, baseRevision);
  }

  public static boolean checkStrategy(RevisionCreator creator, RevisionAccess strategy) {
    return strategy == RevisionAccess.ACCESS_DEFAULT;
  }
}
