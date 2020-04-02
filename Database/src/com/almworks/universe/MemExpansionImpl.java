package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.util.commons.Procedure;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
class MemExpansionImpl extends AbstractExpansionImpl {
  public MemExpansionImpl(State state) {
    super(state);
  }

  public synchronized Result commit(Procedure<Atom[]> onCommitedAtoms) {
    if (myFinished)
      throw new IllegalStateException();
    synchronized (myCommitLock) {
      myFinished = true;
      myCommitted = true;

      removeDiscarded();
      ResultImpl result = new ResultImpl();
      try {
        if (myAtoms.size() > 0) {
          for (Iterator<Verifier> iterator = myVerifiers.iterator(); iterator.hasNext();)
            iterator.next().verify();
          Atom[] atoms = myAtoms.values().toArray(new Atom[myAtoms.size()]);
          myCommitUCN = myState.commitAndPromoteUCN(atoms);
          if (onCommitedAtoms != null)
            onCommitedAtoms.invoke(atoms);
        } else {
          myCommitUCN = myState.commitAndPromoteUCN(null);
          if (onCommitedAtoms != null)
            onCommitedAtoms.invoke(EMPTY);
        }
        result.setSuccessful(true);
      } catch (ExpansionVerificationException e) {
        result.setVerificationException(e);
        result.setSuccessful(false);
      } catch (Exception e) {
        result.setException(e);
        result.setSuccessful(false);
      } finally {
        cleanUp();
      }
      return result;
    }
  }
}
