package com.almworks.database.objects.remote;

import com.almworks.api.database.Revision;

public class IllegalBaseRevisionException extends IllegalArgumentException {
  private final Revision myRevision;

  public IllegalBaseRevisionException(Revision revision) {
    super("cannot change artifact based on a local revision in a closed chain (" + revision + ")");
    myRevision = revision;
  }

  public Revision getRevision() {
    return myRevision;
  }

  @Override
  public String toString() {
    return "IllegalBaseRevisionException{" + "myRevision=" + myRevision + '}';
  }
}
