package com.almworks.api.dynaforms;

public abstract class AbstractFacelet implements Facelet {
  private final String myId;
  private final String myDisplayName;
  private final String myShortId;

  protected AbstractFacelet(String id, String shortId, String displayName) {
    myId = id;
    myShortId = shortId;
    myDisplayName = displayName;
  }

  public String getId() {
    return myId;
  }

  public String getShortId() {
    return myShortId;
  }

  public String getDisplayName() {
    return myDisplayName;
  }
}
