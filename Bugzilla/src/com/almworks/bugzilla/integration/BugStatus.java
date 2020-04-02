package com.almworks.bugzilla.integration;

import com.almworks.util.Enumerable;
import com.almworks.util.Env;
import org.almworks.util.*;

import java.util.*;

public class BugStatus extends Enumerable<BugStatus> {
  public static final BugStatus UNCONFIRMED = new BugStatus("UNCONFIRMED");
  public static final BugStatus NEW = new BugStatus("NEW");
  public static final BugStatus ASSIGNED = new BugStatus("ASSIGNED");
  public static final BugStatus REOPENED = new BugStatus("REOPENED");
  public static final BugStatus RESOLVED = new BugStatus("RESOLVED");
  public static final BugStatus VERIFIED = new BugStatus("VERIFIED");
  public static final BugStatus CLOSED = new BugStatus("CLOSED");

  private static final Map<String, BugStatus> ourMappedStatuses = buildMappedStatuses();

  private static Map<String, BugStatus> buildMappedStatuses() {
    HashMap<String, BugStatus> result = Collections15.hashMap();
    Collection<BugStatus> stati = Enumerable.getAll(BugStatus.class);
    if (stati.size() != 7) {
      assert false : "statuses are not initialized";
      Log.warn("statuses are not initialized");
      return Collections15.emptyMap();
    }
    for (BugStatus status : stati) {
      String altName = Env.getString("bugzilla.status." + Util.lower(status.getName()));
      if (altName != null) {
        altName = Util.upper(altName.trim());
        if (altName.length() > 0) {
          result.put(altName, status);
        }
      }
    }
    return result;
  }

  private BugStatus(String name) {
    super(name);
  }

  public static BugStatus forName(String name) throws BadName {
    if (name == null)
      return null;
    String normName = Util.upper(name).trim();
    BugStatus status;
    status = ourMappedStatuses.get(normName);
    if (status == null)
      status = forName(BugStatus.class, normName);
    if (status == null)
      throw new BadName(name);
    return status;
  }

  public boolean isClosed() {
    return !isOpen();
  }

  public boolean isOpen() {
    return this == UNCONFIRMED || this == NEW || this == ASSIGNED || this == REOPENED;
  }

  public static int count() {
    return Enumerable.count(BugStatus.class);
  }

  public static class BadName extends Exception {
    private final String myRequestedName;

    private BadName(String requestedName) {
      super("bad status " + requestedName);
      myRequestedName = requestedName;
    }

    public String getRequestedName() {
      return myRequestedName;
    }
  }
}
