package com.almworks.bugzilla.integration.data;

import com.almworks.util.files.FileUtil;

public class PLoadQuery {
  private final String myString;

  private PLoadQuery(String string) {
    myString = string;
  }

  public String getString() {
    return myString;
  }

  public static PLoadQuery bugsDownloaded(int bugs, int count) {
    return new PLoadQuery("read " + bugs + " of " + count + " bug records");
  }

  public static PLoadQuery bugsDownloadedPercent(int bugs, int percent) {
    return new PLoadQuery("read " + bugs + " bug ids (" + Math.max(Math.min(100, percent), 0) + "%)");
  }

  public static PLoadQuery bytesDownloaded(long length) {
    return new PLoadQuery("read " + FileUtil.getSizeString(length));
  }

  public static PLoadQuery waiting() {
    return new PLoadQuery("waiting for reply from server");
  }

  public static PLoadQuery parsing() {
    return new PLoadQuery("analyzing reply");
  }

  public String toString() {
    return "Executing Bugzilla search (" + getString() + ")";
  }
}
