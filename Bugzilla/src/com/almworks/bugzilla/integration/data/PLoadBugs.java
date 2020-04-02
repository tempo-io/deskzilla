package com.almworks.bugzilla.integration.data;

public class PLoadBugs {
  private final String myString;

  private PLoadBugs(String string) {
    myString = string;
  }

  public String getString() {
    return myString;
  }

  public static PLoadBugs bugsDownloaded(int downloaded, int total, boolean waitingReply) {
    String string = downloaded + " of " + total + " loaded";
    return new PLoadBugs(waitingReply ? string + ", waiting for reply from server" : string);
  }

  public static PLoadBugs waiting() {
    return new PLoadBugs("waiting for reply from server");
  }
}
