package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.universe.data.ExpansionInfo;
import com.almworks.universe.optimize.UniverseMemoryOptimizer;
import com.almworks.util.Env;
import com.almworks.util.collections.QuickLargeList;
import com.almworks.util.commons.Condition;
import javolution.util.SimplifiedFastMap;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import util.concurrent.SynchronizedBoolean;

import java.util.Collection;
import java.util.Map;

class State {
  private static final boolean MEMORY_OPTIMIZER_ENABLED = !Env.getBoolean("disable.mem.optimizer", false);

  private static final long SPECIAL_ATOM = Long.MAX_VALUE;
  static final Atom NEVER_ATOM = new Atom(SPECIAL_ATOM, 0);
  static final Atom ATOM_PENDING = new Atom(SPECIAL_ATOM, 0);

  private static final long START_UCN = 1;
  private final IndexInternal myGlobalIndex;
  private final QuickLargeList<Atom> myAtoms = new QuickLargeList<Atom>(15);
//  private final SimplifiedFastTable<Atom> myAtoms = SimplifiedFastTable.create();
  private final SimplifiedFastMap<String, IndexInternal> myIndices = SimplifiedFastMap.<String, IndexInternal>create().setShared(true);
  private final SynchronizedBoolean myReadOnly = new SynchronizedBoolean(false);

  private volatile boolean myDefaultIndexing = true;
  private volatile long myUCN = START_UCN;

  private final UniverseMemoryOptimizer myMemoryOptimizer = new UniverseMemoryOptimizer();

  public State() {
    synchronized (this) {
      myAtoms.add(NEVER_ATOM);
    }
    IndexInfo globalInfo = new IndexInfo(".GLOBAL", new GlobalComparator(), Condition.<Atom>always());
    myGlobalIndex = IndexFactory.createIndex(this, globalInfo);
    addIndex(myGlobalIndex);
  }

  public void addIndex(IndexInternal index) {
    myIndices.put(index.getInfo().getName(), index);
  }

  public synchronized void expansionRead(ExpansionInfo info) {
    if (info.UCN < myUCN) {
      throw new IllegalArgumentException(
        "bad expansion info " + info + ": not monotonous ucn [" + info.UCN + " < " + myUCN + "]");
    }

    for (Atom atom : info.atoms) {
      int id = (int) atom.getAtomID();
      if (myAtoms.size() > id) {
        if (myAtoms.get(id) != ATOM_PENDING)
          throw new IllegalArgumentException("bad expansion info " + info + ": atom slot " + id + " is taken");
      } else {
        while (myAtoms.size() <= id)
          myAtoms.add(ATOM_PENDING);
      }
      optimizeAndStoreAtom(id, atom);
    }
    myUCN = info.UCN + 1;
  }

  private synchronized void optimizeAndStoreAtom(int id, Atom atom) {
    if (MEMORY_OPTIMIZER_ENABLED)
      myMemoryOptimizer.optimize(atom);
    myAtoms.set(id, atom);
  }

  public Atom getAtom(long atomID) {
    try {
      Atom atom = myAtoms.get((int) atomID);
      return (atom == null || atom.getAtomID() == SPECIAL_ATOM) ? null : atom;
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  Atom getAtomOrSpecial(long atomID) {
    if (atomID >= 0 && atomID < myAtoms.size()) {
      Atom atom = myAtoms.get((int) atomID);
      assert atom != null;
      return atom;
    } else {
      return null;
    }
  }

  public int getAtomCount() {
    return myAtoms.size();
  }

  private long advanceUCN() {
    return myUCN++;
  }

  synchronized long allocateSlot() {
    myAtoms.add(ATOM_PENDING);
    return myAtoms.size() - 1;
  }

  Index getGlobalIndex() {
    return myGlobalIndex;
  }

  Index getIndex(String indexName) {
    return myIndices.get(indexName);
  }

  Index getIndex(int indexID) {
    for (SimplifiedFastMap.Entry<String, IndexInternal> e = myIndices.head(), end = myIndices.tail(); (e = e.getNext()) != end;) {
      IndexInternal index = e.getValue();
      if (index.getInfo().getIndexID() == indexID)
        return index;
    }
    return null;
  }

  Collection<IndexInternal> getIndices() {
    return Collections15.arrayList(myIndices.values());
  }

  long getUCN() {
    return myUCN;
  }

  boolean isDefaultIndexing() {
    return myDefaultIndexing;
  }

  void setDefaultIndexing(boolean defaultIndexing) {
    myDefaultIndexing = defaultIndexing;
  }

  synchronized void putAtom(long slot, Atom atom) {
    int intSlot = (int) slot;
    assert myAtoms.get(intSlot) == ATOM_PENDING;
    assert atom.getAtomID() == slot;
    optimizeAndStoreAtom(intSlot, atom);
//    System.out.println("put " + intSlot);
  }

  void setReadOnly(boolean readOnly) {
    myReadOnly.set(readOnly);
  }

  void checkWriteAccess() {
    if (myReadOnly.get())
      throw new IllegalStateException("Universe is read-only");
  }

  public synchronized long commitAndPromoteUCN(Atom[] atoms) {
    long ucn = advanceUCN();
    if (atoms != null) {
      for (Atom atom : atoms) {
        atom.buildFinished(ucn);
        putAtom(atom.getAtomID(), atom);
      }
    }
    return ucn;
  }

  public synchronized int cleanPendingAtoms() {
    int size = myAtoms.size();
    int count = 0;
    for (int i = 0; i < size; i++) {
      Atom atom = myAtoms.get(i);
      if (atom == ATOM_PENDING) {
        myAtoms.set(i, NEVER_ATOM);
        count++;
      }
    }
    return count;
  }

  public synchronized void forgetAtom(Atom atom) {
    int id = (int)atom.getAtomID();
    assert id >= 0 && id < myAtoms.size() : atom;
    Atom oldAtom = myAtoms.set(id, NEVER_ATOM);
    assert oldAtom == ATOM_PENDING || oldAtom == NEVER_ATOM : atom;
    if (oldAtom != ATOM_PENDING && oldAtom != NEVER_ATOM) {
      // set back
      myAtoms.set(id, oldAtom);
      Log.debug("failed to forget real atom " + oldAtom + " " + atom);
    }
  }

  public String getIndexStats() {
    StringBuffer result = new StringBuffer();
    int indices = myIndices.size();
    result.append("Indices: ").append(indices).append('\n');
    for (Map.Entry<String, IndexInternal> entry : myIndices.entrySet()) {
      result.append(entry.getKey()).append(": ");
      entry.getValue().writeStats(result);
      result.append('\n');
    }
    return result.toString();
  }
}
