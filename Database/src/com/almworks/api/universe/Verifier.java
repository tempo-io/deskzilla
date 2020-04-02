package com.almworks.api.universe;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Verifier {
  /**
   * Should be reasonably fast!
   *
   * @throws ExpansionVerificationException
   */
  void verify() throws ExpansionVerificationException;
}
