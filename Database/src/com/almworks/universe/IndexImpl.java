package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.util.files.FileUtil;
import org.almworks.util.Collections15;

import java.util.*;

class IndexImpl extends IndexInternal {
  private static int ourNextIndexID = 1;

  private final State myState;
  private final IndexInfo myInfo;
  private final long myMinUCN;
  private final long myMaxUCN;
  private final TreeSet mySet;
  private final Set<Long> myEtherealSlots = Collections15.hashSet();

  private long myLastUpdateUCN = -1;
  private long myLastAtomIndex = 0;

  public IndexImpl(State state, IndexInfo indexInfo, long minUCN, long maxUCN) {
    synchronized (IndexImpl.class) {
      myState = state;
      myInfo =
        new IndexInfo(ourNextIndexID++, indexInfo.getName(), indexInfo.getComparator(), indexInfo.getCondition());
      myMinUCN = minUCN;
      myMaxUCN = maxUCN;
      mySet = new TreeSet<Atom>(createUcnComparator(myInfo.getComparator()));
    }
  }

  public IndexImpl(State state, IndexInfo indexInfo) {
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
    SortedSet sortedSet = mySet.tailSet(sampleParticle);
    if (sortedSet.isEmpty())
      return null;
    Atom atom = (Atom) sortedSet.first();
    if (myInfo.getComparator().compare(atom, sampleParticle) != 0)
      atom = null;
    return atom;
  }

  public synchronized Iterator<Atom> search(final Object sample) {
    update();
    return ConcurrentSortedSetIterator.create(mySet.tailSet(Particle.create(sample)), this);
  }

  public synchronized Iterator<Atom> all() {
    update();
    return ConcurrentSortedSetIterator.create(mySet, this);
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

      while (myLastAtomIndex < myState.getAtomCount()) {
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
    SortedSet clone = (SortedSet) mySet.clone();
    for (Iterator<Object> ii = clone.iterator(); ii.hasNext();) {
      Atom atom = (Atom) ii.next();
      StringBuffer buf = new StringBuffer("A:").append(atom.getAtomID()).append(" { ");
      Map<Long, Particle> map = atom.copyJunctions();
      for (Iterator<Map.Entry<Long, Particle>> jj = map.entrySet().iterator(); jj.hasNext();) {
        Map.Entry<Long, Particle> e = jj.next();
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

  public synchronized int testAtomInSet(Atom atom) {
    int i = 0;
    if (mySet.contains(atom))
      i += 1;
    if (myEtherealSlots.contains(new Long(atom.getAtomID())))
      i += 2;
    return i;
  }

  public StringBuffer writeStats(StringBuffer result) {
    int elems = mySet.size();
    result.append("tree ").append(elems).append(" (").append(FileUtil.getSizeString(32L * elems)).append(")");
    result.append(", ethereal ").append(myEtherealSlots.size());
    return result;
  }
}
