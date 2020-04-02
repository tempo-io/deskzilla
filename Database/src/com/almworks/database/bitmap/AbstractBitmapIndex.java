package com.almworks.database.bitmap;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.database.filter.SystemFilter;
import com.almworks.util.threads.Threads;
import org.jetbrains.annotations.*;
import util.concurrent.ReadWriteLock;
import util.concurrent.Sync;
import util.external.BitSet2;

import java.util.Iterator;


public abstract class AbstractBitmapIndex {
  protected final Basis myBasis;
  private final ReadWriteLock myUpdateLock;
  private final SystemFilter mySystemFilter;
  private final IndexKey myKey;

  private BitSet2 myData = new BitSet2();

  /**
   * The last copy of myData, nulled when myData is modified.
   */
  private BitSet2 myPublicData = null;

  private WCN myWCN = WCN.EARLIEST;

  private boolean myDirty = false;
  private boolean myLoaded = false;

  public AbstractBitmapIndex(RevisionAccess strategy, SystemFilter systemFilter, ReadWriteLock updateLock, Basis basis) {
    assert strategy != null;
    assert updateLock != null;
    mySystemFilter = systemFilter;
    myKey = new IndexKey(systemFilter.getPersistableKey(), strategy);
    myUpdateLock = updateLock;
    myBasis = basis;
  }

//public static final DebugDichotomy __stat = new DebugDichotomy("abi:cached", "abi:updated", 100);

  /**
   * Returns shared bitset for atoms that correspond to the index.
   * DO NOT MODIFY
   */
  public BitSet2 getAtomBitsForReading() throws InterruptedException {
    Sync sync = myUpdateLock.readLock();
    sync.acquire();
    try {
      synchronized(this) {
        BitSet2 publicData = myPublicData;
        if (publicData != null) {
//          __stat.a();
          return publicData;
        }

//        __stat.b();
        BitSet2 data = myData.unmodifiableCopy();
        myPublicData = data;
        return data;
      }
    } finally {
      sync.release();
    }
  }

  public BitSet2 applyImmediatelyTo(BitSet2 bitSet) throws InterruptedException {
    // if we need to make a copy, do it not within locks
    BitSet2 modifiable = bitSet.modifiable();
    
    Sync sync = myUpdateLock.readLock();
    sync.acquire();
    try {
      synchronized(this) {
        return modifiable.and(myData);
      }
    } finally {
      sync.release();
    }
  }

/*
  private void __debug() {
    if (Debug.__tracked_atom > 0) {
      boolean b = myData.access((int)Debug.__tracked_atom);
      Log.debug(">>> " + (b ? "TRUE  - " : "FALSE - ") + myKey);
    }
  }
*/

  public RevisionAccess getStrategy() {
    return myKey.getStrategy();
  }

  public SystemFilter getSystemFilter() {
    return mySystemFilter;
  }

  public synchronized void updateBit(long id, boolean set) {
    boolean changed = myData.set((int) id, set);
    if (changed) {
      myDirty = true;
      myPublicData = null;
    }
  }

  public synchronized void updateWCN(WCN wcn) {
    assert wcn != null;
//    assert myWCN.getUCN() <= wcn.getUCN() : wcn + " " + myWCN;
    if (wcn == null || wcn.getUCN() < myWCN.getUCN())
      return;
    myWCN = wcn;
    myDirty = true;
  }

  protected synchronized WCN getWCN() {
    return myWCN;
  }

  protected void rollForwardNewIndex(Atom atom) {
    updateIndex(atom);
  }

  synchronized void rollForward() {
    WCN wcn = getWCN();
    long currentUCN = myBasis.getUnderlyingUCN();
    if (wcn.getUCN() >= currentUCN)
      return;

    boolean empty = !myLoaded;

    Iterator<Atom> ii = myBasis.ourUniverse.getGlobalIndex().all();
    while (ii.hasNext()) {
      Atom atom = ii.next();
      if (atom.getUCN() < wcn.getUCN())
        break;
      if (empty) {
        // build new index
        rollForwardNewIndex(atom);
      } else {
        // catch up with lost updates for old index
        updateIndex(atom);
      }
    }
    updateWCN(WCN.createWCN(currentUCN));
  }

  protected abstract void updateIndexWithCorrectStrategy(Revision revision);

  void updateIndex(@NotNull Revision revision) {
    long atomID = myBasis.getAtomID(revision);
    Revision correct = myBasis.getRevisionOrNull(atomID, getStrategy());
    if (correct != null)
      updateIndexWithCorrectStrategy(correct);
  }

  private void updateIndex(Atom atom) {
    if (myBasis.isRevisionAtom(atom)) {
      Revision revision = myBasis.getRevisionOrNull(atom.getAtomID(), getStrategy());
      if (revision != null)
        updateIndexWithCorrectStrategy(revision);
    }
  }

  public synchronized boolean isDirty() {
    return myDirty;
  }

  public synchronized void loadData(BitmapIndexInfo indexInfo) {
    assert !myLoaded;
    if (indexInfo == null)
      return;
    BitSet2 bits = indexInfo.getBits();
    if (bits == null)
      return;
    WCN wcn = indexInfo.getWCN();
    if (wcn == null)
      return;

    myData = bits;
    myPublicData = null;
    myWCN = wcn;
    myLoaded = true;
  }

  public IndexKey getKey() {
    return myKey;
  }

  synchronized BitSet2 getAtomBitsInternal() {
    return myData;
  }

  synchronized void clearDirty() {
    myDirty = false;
  }

  // index is corrupt, rebuild
  synchronized void drop() {
    Threads.assertLongOperationsAllowed();
    myData.clear();
    myPublicData = null;
    myWCN = WCN.EARLIEST;
    myLoaded = false;
    myDirty = true;
  }
}
