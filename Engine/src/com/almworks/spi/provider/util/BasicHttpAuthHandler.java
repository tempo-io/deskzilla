package com.almworks.spi.provider.util;

import com.almworks.api.http.HttpCancelledException;
import com.almworks.api.http.auth.*;
import com.almworks.api.passman.PMDomain;
import com.almworks.api.passman.PasswordManager;
import com.almworks.util.Pair;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Computable;

public class BasicHttpAuthHandler extends AbstractFeedbackHandler {
  private final HttpAuthDialog myDialog;

  public BasicHttpAuthHandler(PasswordManager passwordManager, HttpAuthDialog dialog) {
    super(passwordManager);
    myDialog = dialog;
  }

  protected Pair<HttpAuthCredentials,HttpAuthPersistOption> doRequest(PMDomain domain, final HttpAuthChallengeData data,
    final HttpAuthCredentials failed, final boolean proxy) throws HttpCancelledException, InterruptedException {

    return ThreadGate.AWT_IMMEDIATE.compute(new Computable<Pair<HttpAuthCredentials,HttpAuthPersistOption>>() {
      public Pair<HttpAuthCredentials,HttpAuthPersistOption> compute() {
        return myDialog.show(data, failed, proxy);
      }
    });
  }
}
