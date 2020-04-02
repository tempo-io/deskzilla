package com.almworks.bugzilla.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.ConnectionSynchronizer;
import com.almworks.api.engine.SyncProblem;
import com.almworks.api.http.auth.*;
import com.almworks.util.L;
import com.almworks.util.Pair;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Util;

import java.util.Date;

/**
 * :todoc:
 *
 * @author sereda
 */
public class HttpAuthRequested implements SyncProblem {
  private final HttpAuthChallengeData myChallengeData;
  private final HttpAuthCredentials myFailedCredentials;
  private final Date myCreationDate = new Date();
  private final Runnable myOnResolved;
  private final HttpAuthDialog myDialog;

  private HttpAuthCredentials myCredentials = null;
  private HttpAuthPersistOption myPersistOption = null;
  private final ComponentContainer myContainer;
  private final boolean myProxy;

  public HttpAuthRequested(HttpAuthChallengeData data, HttpAuthCredentials failed, boolean proxy,
    ComponentContainer container, Runnable onResolved)
  {
    assert onResolved != null;
    assert data != null;
    myChallengeData = data;
    myFailedCredentials = failed;
    myOnResolved = onResolved;
    myDialog = container.getActor(HttpAuthDialog.ROLE);
    myContainer = container;
    myProxy = proxy;
  }

  public ConnectionSynchronizer getConnectionSynchronizer() {
    return myContainer.getActor(ConnectionSynchronizer.ROLE);
  }

  public Date getTimeHappened() {
    return myCreationDate;
  }

  public String getShortDescription() {
    return L.content(myProxy ? "Proxy authentication requested" : "Authentication requested");
  }

  public String getMediumDescription() {
    return getShortDescription();
  }

  public String getLongDescription() {
    StringBuffer buf = new StringBuffer(myProxy ? "The proxy server has requested HTTP PROXY authentication.\n" : "The web server has requested HTTP authentication.\n");
    buf.append(myChallengeData.getStatusCode()).append(" ").append(myChallengeData.getStatusText()).append("\n");
    buf.append("Authentication method: ")
      .append(Util.NN(Util.upper(myChallengeData.getAuthScheme())))
      .append("\n");
    buf.append("Realm: ").append(myChallengeData.getRealm());
    if (myFailedCredentials != null) {
      String username = myFailedCredentials.getUsername();
      if (username != null) {
        username = username.trim();
        if (username.length() > 0) {
          buf.append("\n\nAttempt to authenticate using user name '").append(username).append("'");
          String password = myFailedCredentials.getPassword();
          if (password != null && password.trim().length() > 0)
            buf.append(" and a password");
          buf.append(" was made.");
        }
      }
    }
    return buf.toString();
  }

  public boolean isResolvable() {
    return true;
  }

  public void resolve(ActionContext context) throws CantPerformException {
    Pair<HttpAuthCredentials, HttpAuthPersistOption> r = myDialog.show(myChallengeData, myFailedCredentials, myProxy);
    if (r == null)
      return;
    myCredentials = r.getFirst();
    myPersistOption = r.getSecond();
    myOnResolved.run();
  }

  public boolean isSerious() {
    return false;
  }

  public HttpAuthCredentials getCredentials() {
    return myCredentials;
  }

  public HttpAuthPersistOption getPersistOption() {
    return myPersistOption;
  }
}
