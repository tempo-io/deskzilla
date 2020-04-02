package com.almworks.bugzilla.integration.data;

import org.jetbrains.annotations.*;

public class StatusInfo {
  private final String myStatus;

  /**
   * Tells whether status is "open" or "closed". If null, unknown.
   */
  @Nullable
  private final Boolean myOpen;

  public StatusInfo(String status, @Nullable Boolean open) {
    myStatus = status;
    myOpen = open;
  }

  public String getStatus() {
    return myStatus;
  }

  @Nullable
  public Boolean isOpen() {
    return myOpen;
  }

  @Override
  public String toString() {
    return "SI " + myStatus + ": " + String.valueOf(myOpen); 
  }
}
