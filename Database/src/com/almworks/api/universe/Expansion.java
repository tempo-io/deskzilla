package com.almworks.api.universe;

import com.almworks.util.commons.Procedure;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface Expansion {
  long getStartUCN();

  long getCommitUCN();

  Atom createAtom();

  /**
   * Removes atom from transaction. Atom should not have registered links. If anybody
   * registers a link before call to commit() (and even after discard()), atom will be
   * included in expansion.
   * @param atom
   */
  void discardAtom(Atom atom);

  /**
   * Declares that atom is referred in the transaction and could not be discarded.
   * @param atom
   */
  void atomReferred(Atom atom);

  void atomReferred(long atomID);

  /**
   * Verifier will be called under commit lock.
   *
   * @param verifier
   */
  void addVerifier(Verifier verifier);

  Expansion.Result commit();

  Expansion.Result commit(Procedure<Atom[]> onCommitedAtoms);

  void rollback();

  interface Result {
    boolean isSuccessful();

    ExpansionVerificationException getVerificationException();

    Throwable getException();
  }
}
