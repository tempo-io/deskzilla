package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.database.filter.SystemFilter;
import com.almworks.database.objects.ArtifactProxy;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;
import util.concurrent.ReadWriteLock;
import util.concurrent.Synchronized;

import java.util.List;

class LastRevisionBitmapIndex extends AbstractBitmapIndex {
  private final List<Revision> myUpdateList = Collections15.arrayList();
  private final Synchronized<Thread> myUpdatingThread = new Synchronized<Thread>(null);

  public LastRevisionBitmapIndex(RevisionAccess strategy, SystemFilter systemFilter, ReadWriteLock updateLock,
    Basis basis)
  {
    super(strategy, systemFilter, updateLock, basis);
  }

  protected void updateIndexWithCorrectStrategy(Revision revision) {
    Thread thread = Thread.currentThread();
    boolean allowed = myUpdatingThread.commit(null, thread);
    if (!allowed) {
      assert false : this + " " + thread + " " + myUpdatingThread.get();
      return;
    }
    try {
      RevisionAccess strategy = getStrategy();
      Artifact artifact = revision.getArtifact();
      if (!artifact.isAccessStrategySupported(strategy))
        return;
      Revision rev = artifact.getLastRevision(strategy);
      long lastAtomID = myBasis.getAtomID(rev);

      Revision prev = rev.getPrevRevision();
      if (prev != null) {
        updateBit(myBasis.getAtomID(prev), false);
      }

      RCBArtifact rcb = artifact.getRCBExtension(false);
      if (rcb != null) {
        myUpdateList.clear();
        rcb.getCompleteRevisionsList(myUpdateList);
        for (Revision r : myUpdateList) {
          long atomID = myBasis.getAtomID(r);
          if (atomID != lastAtomID)
            updateBit(atomID, false);
        }
        myUpdateList.clear();
      }

      updateBit(lastAtomID, true);
    } finally {
      boolean success = myUpdatingThread.commit(thread, null);
      assert success : this;
    }
  }

  protected synchronized void updateIndex(@NotNull Revision revision) {
    // strategy is irrelevant here
    updateIndexWithCorrectStrategy(revision);
  }

  protected void rollForwardNewIndex(Atom atom) {
    if (!myBasis.isArtifactAtom(atom))
      return;
    ArtifactProxy artifact = myBasis.getArtifact(atom.getAtomID());
    RevisionAccess strategy = getStrategy();
    if (!artifact.isAccessStrategySupported(strategy))
      return;
    Revision revision = artifact.getChain(strategy).getLastRevisionOrNull(WCN.LATEST);
    if (revision == null)
      return;
    updateBit(myBasis.getAtomID(revision), true);
  }
}
