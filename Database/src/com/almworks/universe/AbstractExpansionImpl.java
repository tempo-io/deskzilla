package com.almworks.universe;

import com.almworks.api.universe.*;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class AbstractExpansionImpl implements Expansion {
  protected final State myState;
  protected final long myStartUCN;
  //protected SortedMap<Long, Atom> myAtoms = Collections15.treeMap();
  protected Map<Long, Atom> myAtoms = Collections15.hashMap();
  protected List<Verifier> myVerifiers = Collections15.arrayList();
  protected boolean myFinished = false;
  protected boolean myCommitted = false;
  protected static final Object myCommitLock = new Object(); // todo move to state
  protected long myCommitUCN = -1;
  protected static final Atom[] EMPTY = {};

  public AbstractExpansionImpl(State state) {
    assert state != null;
    myState = state;
    myStartUCN = myState.getUCN();
  }

  public long getStartUCN() {
    return myStartUCN;
  }

  public synchronized Atom createAtom() {
    final long slot = myState.allocateSlot();
    Atom atom = new Atom(slot, 0);
    myAtoms.put(new Long(slot), atom);
    return atom;
  }

  public synchronized void discardAtom(Atom atom) {
    Atom stored = myAtoms.get(new Long(atom.getAtomID()));
    assert stored == atom;
    if (stored == atom)
      atom.setDiscarding();
  }

  public synchronized void atomReferred(Atom atom) {
    Atom stored = myAtoms.get(new Long(atom.getAtomID()));
    assert stored == atom;
    if (stored == atom)
      atom.setReferred();
  }

  public synchronized void atomReferred(long atomID) {
    Atom stored = myAtoms.get(new Long(atomID));
    if (stored == null)
      return;
    stored.setReferred();
  }

  public synchronized void addVerifier(Verifier verifier) {
    myVerifiers.add(verifier);
  }

  public synchronized void rollback() {
    if (myFinished)
      throw new IllegalStateException();
    myFinished = true;
    myCommitted = false;
    for (Iterator<Atom> ii = myAtoms.values().iterator(); ii.hasNext();) {
      Atom atom = ii.next();
      myState.forgetAtom(atom);
    }
    cleanUp();
  }

  protected synchronized void cleanUp() {
    myAtoms.clear();
    myVerifiers.clear();
  }

  public synchronized long getCommitUCN() {
    return myCommitUCN;
  }

  protected synchronized void removeDiscarded() {
    for (Iterator<Atom> ii = myAtoms.values().iterator(); ii.hasNext();) {
      Atom atom = ii.next();
      if (atom.isDiscarding() && !atom.isReferred()) {
        ii.remove();
        myState.forgetAtom(atom);
      }
    }
  }

  public synchronized Result commit() {
    return commit(null);
  }
}
