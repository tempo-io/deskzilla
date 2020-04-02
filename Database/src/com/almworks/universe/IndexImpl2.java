package com.almworks.universe;

import com.almworks.api.universe.*;
import org.almworks.util.Collections15;

import java.util.*;

class IndexImpl2 extends IndexInternal {
  private static int ourNextIndexID = 1;

  private final State myState;
  private final IndexInfo myInfo;
  private final long myMinUCN;
  private final long myMaxUCN;
  private final CompactingSortedSet<Atom> mySet;
  private final Set<Long> myEtherealSlots = Collections15.hashSet();

  private long myLastUpdateUCN = -1;
  private long myLastAtomIndex = 0;

  public IndexImpl2(State state, IndexInfo indexInfo, long minUCN, long maxUCN) {
    synchronized (IndexImpl2.class) {
      myState = state;
      myInfo = new IndexInfo(IndexImpl2.ourNextIndexID++, indexInfo.getName(), indexInfo.getComparator(),
        indexInfo.getCondition());
      myMinUCN = minUCN;
      myMaxUCN = maxUCN;
      Comparator comparator = myInfo.getComparator();
      mySet = new CompactingSortedSet(comparator, createUcnComparator(comparator));
    }
  }

  public IndexImpl2(State state, IndexInfo indexInfo) {
    this(state, indexInfo, Universe.BIG_BANG, Universe.END_OF_THE_UNIVERSE);
  }

  public int getSelectivityHint() {
    return 0;
  }

  public synchronized Atom first() {
    update();
    Atom atom = mySet.isEmpty() ? null : (Atom) mySet.first();
    return atom;
  }

  public synchronized Atom last() {
    update();
    Atom atom = mySet.isEmpty() ? null : (Atom) mySet.last();
    return atom;
  }

  public synchronized Atom searchExact(Object sample) {
    update();
    Particle sampleParticle = Particle.create(sample);
    return mySet.searchExact(sampleParticle);
  }

  public synchronized Iterator<Atom> search(final Object sample) {
    update();
    Particle sampleParticle = Particle.create(sample);
    return mySet.iterator(sampleParticle, this);
  }

  public synchronized Iterator<Atom> all() {
    update();
    return mySet.iterator(null, this);
  }

  public IndexInfo getInfo() {
    return myInfo;
  }

  private void update() {
    synchronized (myState) {
      if (myLastUpdateUCN >= myState.getUCN())
        return;
      for (Iterator<Long> it = myEtherealSlots.iterator(); it.hasNext();) {
        Atom atom = myState.getAtomOrSpecial(it.next().longValue());
        assert atom != null;
        if (atom == null)
          continue;
        if (atom == State.ATOM_PENDING)
          continue;
        if (atom != State.NEVER_ATOM)
          updateAtom(atom);
        it.remove();
      }

      int atomCount = myState.getAtomCount();
      if (myLastAtomIndex < atomCount) {
        mySet.startAdding(atomCount - myLastAtomIndex);
        try {
          while (myLastAtomIndex < atomCount) {
            long slot = myLastAtomIndex++;
            Atom atom = myState.getAtomOrSpecial(slot);
            assert atom != null;
            if (atom == null)
              continue;
            if (atom == State.ATOM_PENDING)
              myEtherealSlots.add(new Long(slot));
            else if (atom != State.NEVER_ATOM)
              updateAtom(atom);
          }
        } finally {
          mySet.stopAdding();
        }
      }
      myLastUpdateUCN = myState.getUCN();
    }
  }

  private void updateAtom(Atom atom) {
    assert atom != State.ATOM_PENDING;
    assert atom != State.NEVER_ATOM;
    if (atom.getUCN() >= myMinUCN && atom.getUCN() < myMaxUCN) {
      if (myInfo.getCondition().isAccepted(atom)) {
        mySet.add(atom);
      }
    }
  }

  /**
   * debug method
   */
  synchronized void dumpIndex() {
    System.out.println("ATOMS:");
    for (Iterator<Atom> ii = all(); ii.hasNext();) {
      Atom atom = ii.next();
      StringBuffer buf = new StringBuffer("A:").append(atom.getAtomID()).append(" { ");
      Map<Long, Particle> map = atom.copyJunctions();
      for (Map.Entry<Long, Particle> e : map.entrySet()) {
        buf.append(e.getKey()).append(":").append(e.getValue()).append(" ");
      }
      buf.replace(buf.length() - 1, buf.length(), "}");
      System.out.println(buf.toString());
    }

    Long[] ethereals = myEtherealSlots.toArray(new Long[myEtherealSlots.size()]);
    StringBuffer buf = new StringBuffer("\n\nETHEREAL: ");
    for (int i = 0; i < ethereals.length; i++)
      buf.append(ethereals[i]).append(" ");
    System.out.println(buf.toString());

    System.out.println("myLastUpdateUCN=" + myLastUpdateUCN);
    System.out.println("myLastAtomIndex=" + myLastAtomIndex);
  }

  public StringBuffer writeStats(StringBuffer result) {
    mySet.writeStats(result);
    result.append(", ethereal ").append(myEtherealSlots.size());
    return result;
  }

  public int testAtomInSet(Atom atom) {
    int i = 0;
    if (mySet.contains(atom))
      i += 1;
    if (myEtherealSlots.contains(new Long(atom.getAtomID())))
      i += 2;
    return i;
  }
}
