package com.almworks.spi.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.ConnectionSynchronizer;
import com.almworks.api.engine.SyncProblem;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;

import java.util.Date;

public class HttpConnectionProblem implements SyncProblem {
  private final ConnectorException myException;
  private final Date myCreationTime;
  private final ComponentContainer myContainer;

  public HttpConnectionProblem(ComponentContainer container, ConnectorException exception) {
    myContainer = container;
    myException = exception;
    myCreationTime = new Date();
  }

  public String getLongDescription() {
    return myException.getLongDescription();
  }

  public String getMediumDescription() {
    return myException.getMediumDescription();
  }

  public String getShortDescription() {
    return myException.getShortDescription();
  }

  public ConnectionSynchronizer getConnectionSynchronizer() {
    return myContainer.getActor(ConnectionSynchronizer.ROLE);
  }

  public Date getTimeHappened() {
    return myCreationTime;
  }

  public boolean isResolvable() {
    return false;
  }

  public void resolve(ActionContext context) throws CantPerformException {
  }

  public boolean isSerious() {
    return false;
  }
}
