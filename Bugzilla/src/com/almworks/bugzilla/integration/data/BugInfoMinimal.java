package com.almworks.bugzilla.integration.data;

import com.almworks.util.collections.Convertor;
import org.almworks.util.Log;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugInfoMinimal {
  private static final Integer INVALID_BUG_ID = 0;
  public final String myBugID;
  public final String lastModified;
  public static final Convertor<BugInfoMinimal, Integer> EXTRACT_ID = new Convertor<BugInfoMinimal, Integer>() {
    public Integer convert(BugInfoMinimal bugInfo) {
      return bugInfo.getID();
    }
  };

  public BugInfoMinimal(String id, String lastModified) {
    this.myBugID = id;
    this.lastModified = lastModified;
  }

  public String getStringID() {
    return myBugID;
  }

  public String getStringMTime() {
    return lastModified;
  }

  public Integer getID() {
    try {
      return Integer.parseInt(getStringID());
    } catch (NumberFormatException e) {
      Log.warn("invalid bug id [" + myBugID + "]");
      return INVALID_BUG_ID;
    }
  }

  @Override
  public String toString() {
    return myBugID + " [" + lastModified + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    BugInfoMinimal that = (BugInfoMinimal) o;

    if (lastModified != null ? !lastModified.equals(that.lastModified) : that.lastModified != null)
      return false;
    if (myBugID != null ? !myBugID.equals(that.myBugID) : that.myBugID != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBugID != null ? myBugID.hashCode() : 0;
    result = 31 * result + (lastModified != null ? lastModified.hashCode() : 0);
    return result;
  }
}
