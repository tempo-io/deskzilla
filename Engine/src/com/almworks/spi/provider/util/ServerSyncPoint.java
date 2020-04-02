package com.almworks.spi.provider.util;

import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.util.io.persist.*;
import org.almworks.util.Const;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ServerSyncPoint {
  private static final String LAST_SYNC_TIME = "lastsync";
  private static final String LAST_ISSUE_ID = "lastiid";
  private static final String LAST_ISSUE_MTIME = "lastimtime";

  private final long mySyncTime;
  private final int myLatestIssueId;
  private final String myLatestIssueMTime;

  public ServerSyncPoint(long syncTime, int latestIssueId, String latestIssueMTime) {
    mySyncTime = syncTime;
    myLatestIssueId = latestIssueId;
    myLatestIssueMTime = latestIssueMTime;
  }

  public long getSyncTime() {
    return mySyncTime;
  }

  public int getLatestIssueId() {
    return myLatestIssueId;
  }

  public String getLatestIssueMTime() {
    return myLatestIssueMTime;
  }

  public boolean isValidSuccessorState(ServerSyncPoint successorPoint) {
    if (successorPoint.isUnsynchronized() || isUnsynchronized())
      return true;
    if ((getLatestIssueId() <= 0 || getLatestIssueMTime() == null) && (successorPoint.getLatestIssueId() > 0 && successorPoint.getLatestIssueMTime() != null))
      return true;
    return successorPoint.getSyncTime() >= getSyncTime();
  }

  public boolean isUnsynchronized() {
    return mySyncTime <= Const.DAY;
  }

  public static ServerSyncPoint unsynchronized() {
    return new ServerSyncPoint(0, 0, null);
  }

  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(mySyncTime);
    if (mySyncTime >= Const.DAY) {
      DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      format.setTimeZone(TimeZone.getTimeZone("GMT"));
      b.append('[').append(format.format(new Date(mySyncTime))).append(" GMT]");
    }
    b.append(':').append(myLatestIssueId).append(':');
    if (myLatestIssueMTime != null)
      b.append(myLatestIssueMTime);
    return b.toString();
  }

  @NotNull
  public static ServerSyncPoint read(Store store) {
    PersistableLong plong = new PersistableLong();
    PersistableInteger pint = new PersistableInteger();
    PersistableString pstring = new PersistableString();
    boolean s1 = StoreUtils.restorePersistable(store, LAST_SYNC_TIME, plong);
    if (!s1) {
      return unsynchronized();
    }
    Long syncTime = plong.access();
    if (syncTime == null || syncTime < Const.DAY) {
      return unsynchronized();
    }
    boolean s2 = StoreUtils.restorePersistable(store, LAST_ISSUE_ID, pint);
    boolean s3 = StoreUtils.restorePersistable(store, LAST_ISSUE_MTIME, pstring);
    Integer issueId = null;
    String issueMtime = null;
    if (s2 && s3) {
      issueId = pint.access();
      issueMtime = pstring.access();
    }
    return new ServerSyncPoint(syncTime, issueId == null ? 0 : issueId, issueMtime);
  }

  public static void write(ServerSyncPoint point, Store store) {
    StoreUtils.storePersistable(store, LAST_SYNC_TIME, new PersistableLong(point.getSyncTime()));
    StoreUtils.storePersistable(store, LAST_ISSUE_ID, new PersistableInteger(point.getLatestIssueId()));
    StoreUtils.storePersistable(store, LAST_ISSUE_MTIME, new PersistableString(point.getLatestIssueMTime()));
  }
}
