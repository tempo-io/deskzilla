package com.almworks.universe;

import com.almworks.api.universe.Expansion;
import com.almworks.api.universe.ExpansionVerificationException;

/**
 * :todoc:
 *
 * @author sereda
 */
class ResultImpl implements Expansion.Result {
  private boolean mySuccessful;
  private ExpansionVerificationException myVerificationException;
  private Throwable myException;

  public boolean isSuccessful() {
    return mySuccessful;
  }

  public void setSuccessful(boolean successful) {
    mySuccessful = successful;
  }

  public ExpansionVerificationException getVerificationException() {
    return myVerificationException;
  }

  public void setVerificationException(ExpansionVerificationException verificationException) {
    myVerificationException = verificationException;
  }

  public Throwable getException() {
    return myException;
  }

  public void setException(Throwable exception) {
    myException = exception;
  }
}
