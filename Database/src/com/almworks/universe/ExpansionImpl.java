package com.almworks.universe;

import com.almworks.api.universe.*;
import com.almworks.universe.data.*;
import com.almworks.util.commons.Procedure;

/**
 * :todoc:
 *
 * @author sereda
 */
class ExpansionImpl extends AbstractExpansionImpl {
  private final AtomDataFile myDataFile;

  public ExpansionImpl(State state, AtomDataFile dataFile) {
    super(state);
    assert dataFile != null;
    myDataFile = dataFile;
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
          myState.checkWriteAccess();
          for (Verifier verifier : myVerifiers)
            verifier.verify();
          Atom[] atoms = myAtoms.values().toArray(new Atom[myAtoms.size()]);
          myCommitUCN = myState.commitAndPromoteUCN(atoms);
          ExpansionInfo info = new ExpansionInfo(myCommitUCN, atoms);
          myDataFile.write(info);
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
      } catch (AtomDataFileException e) {
        result.setException(e);
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
