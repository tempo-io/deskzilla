package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.database.*;
import com.almworks.database.objects.RevisionWithInternals;
import com.almworks.util.commons.Lazy;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Threads;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import util.external.BitSet2;

public class BitmapRootArtifactView extends ScanningRootArtifactView implements ViewHavingBitmap {
  private final Lazy<AbstractBitmapIndex> myLastRevisionIndex = new Lazy<AbstractBitmapIndex>() {
    public AbstractBitmapIndex instantiate() {
      return myBasis.getBitmapIndexManager().getLastRevisionIndex(myStrategy);
    }
  };

  private final CorruptIndexRebuilder myCorruptRebuilder;

  public BitmapRootArtifactView(Basis basis, RootViewProvider rootProvider, RevisionAccess strategy) {
    super(basis, rootProvider, strategy);
    myCorruptRebuilder = new CorruptIndexRebuilder(basis, myLastRevisionIndex, null);
  }

  protected RootListenerJob createJob(ThreadGate callbackGate, ArtifactListener listener) {
    return new BitmapRootListenerJob(callbackGate, listener);
  }

  public boolean hasAllIndices() {
    return true;
  }

  public boolean isCountQuicklyAvailable() {
    return true;
  }

  public BitSet2 getRevisionBits() {
    try {
      return myLastRevisionIndex.get().getAtomBitsForReading();
    } catch (InterruptedException e) {
      Log.warn(this + " interrupted", e);
      throw new RuntimeInterruptedException(e);
    }
  }

  public void rebuildCorruptIndexes() throws InterruptedException {
    myCorruptRebuilder.rebuildCorruptIndexes();
  }

  private class BitmapRootListenerJob extends RootListenerJob {
    public BitmapRootListenerJob(ThreadGate callbackGate, ArtifactListener listener) {
      super(callbackGate, listener);
    }

    protected void doScanPast(WCN.Range past, WCN.Range future) {
      Threads.assertLongOperationsAllowed();
      long endUCN = past.getEndUCN();
      boolean proceed = true;

      BitSet2 bits = getRevisionBits();

      for (int a = bits.prevSetBit(bits.size()); a >= 0; a = bits.prevSetBit(a - 1)) {
        if (myTerminated.get())
          break;
        RevisionWithInternals revision = myBasis.getRevisionOrNull(a, myStrategy);
        if (revision == null)
          continue;

        Artifact artifact = revision.getArtifact();
        // first revision may appear later than artifact - so check that it is not later than endUCN
        Revision firstRevision = artifact.getChain(myStrategy).getFirstRevision();
        if (firstRevision == null) {
          Log.warn("no first revision (" + myStrategy + ") for artifact " + artifact);
          continue;
        }
        if (firstRevision.getWCN().getUCN() >= endUCN)
          continue;

        proceed = doFireFromPast(artifact, revision, past);
        if (!proceed)
          break;
      }

      if (!proceed)
        cleanUp();
      pastPassed(past, future);
    }
  }
}
