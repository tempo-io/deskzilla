package com.almworks.bugzilla.provider;

import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.bugzilla.integration.data.StatusInfo;
import com.almworks.util.io.persist.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class WorkflowScheme {
  private static final String STATUS_OPEN_MAP_STID = "SOM";
  private static final String WORKFLOW_TRANSITION_MAP_STID = "WTM";
  private static final String DUP_STATUS_NN_STID = "DS.NN";
  private static final String DUP_STATUS_STID_ = "DS";

  /**
   * Contains mapping from status to boolean "is_open". If not mapped, status's status is unknown.
   */
  private final Map<String, Boolean> myStatusOpenMap = Collections15.hashMap();

  /**
   * Contains mapping from status to the list of other statuses directly reachable from this status, excluding
   * original status itself.
   */
  private final Map<String, Set<String>> myWorkflowTransitionMap = Collections15.hashMap();

  /**
   * Status used for "mark as duplicate action"
   */
  @Nullable
  private String myDuplicateStatus;

  // todo track whether a comment is required for a transition

  public synchronized boolean reportWorkflow(String currentStatus, List<StatusInfo> statusChanges,
    @Nullable String markDuplicateStatus)
  {
    if (currentStatus == null || statusChanges == null)
      return false;
    boolean changed = updateOpenMap(statusChanges);
    changed |= updateTransitionMap(currentStatus, statusChanges);
    changed |= updateDuplicateStatus(markDuplicateStatus);
    return changed;
  }

  public synchronized boolean reportStatusInfos(List<StatusInfo> statusInfos) {
    return updateOpenMap(statusInfos);
  }

  public synchronized boolean reportInitialStatuses(List<String> initialStatuses) {
    if (initialStatuses == null || !hasInitialStatusChanges(initialStatuses))
      return false;
    Set<String> set = Collections15.hashSet(initialStatuses);
    myWorkflowTransitionMap.put("", Collections.unmodifiableSet(set));
    return true;
  }

  private boolean hasInitialStatusChanges(List<String> initialStatuses) {
    Set<String> set = myWorkflowTransitionMap.get("");
    if (set == null || set.size() != initialStatuses.size())
      return true;
    for (String initialStatus : initialStatuses) {
      if (!set.contains(initialStatus))
        return true;
    }
    return false;
  }

  private boolean updateDuplicateStatus(String markDuplicateStatus) {
    assert Thread.holdsLock(this);
    if (!Util.equals(myDuplicateStatus, markDuplicateStatus)) {
      myDuplicateStatus = markDuplicateStatus;
      return true;
    } else {
      return false;
    }
  }

  private boolean updateOpenMap(List<StatusInfo> statusChanges) {
    assert Thread.holdsLock(this);
    boolean changed = false;
    for (StatusInfo s : statusChanges) {
      String status = s.getStatus();
      Boolean r = myStatusOpenMap.get(status);
      if (!Util.equals(r, s.isOpen())) {
        myStatusOpenMap.put(status, s.isOpen());
        changed = true;
      }
    }
    return changed;
  }

  private boolean updateTransitionMap(String fromStatus, List<StatusInfo> toStatuses) {
    assert Thread.holdsLock(this);
    if (!hasChanges(fromStatus, toStatuses))
      return false;
    Set<String> set = Collections15.hashSet();
    for (StatusInfo s : toStatuses) {
      set.add(s.getStatus());
    }
    myWorkflowTransitionMap.put(fromStatus, Collections.unmodifiableSet(set));
    return true;
  }

  private boolean hasChanges(String fromStatus, List<StatusInfo> toStatuses) {
    Set<String> targets = myWorkflowTransitionMap.get(fromStatus);
    if (targets == null)
      return true;
    if (targets.size() != toStatuses.size())
      return true;
    for (StatusInfo s : toStatuses) {
      if (!targets.contains(s.getStatus()))
        return true;
    }
    return false;
  }


  @Nullable
  public synchronized Set<String> getAvailableTransitions(String fromStatus) {
    return myWorkflowTransitionMap.get(fromStatus);
  }

  public synchronized void clear() {
    myDuplicateStatus = null;
    myStatusOpenMap.clear();
    myWorkflowTransitionMap.clear();
  }

  private final PersistableString pString = new PersistableString();
  private final PersistableBoolean pBoolean = new PersistableBoolean();
  private final PersistableHashMap<String, Boolean> pStringBooleanMap =
    new PersistableHashMap<String, Boolean>(new PersistableString(), new PersistableBoolean());
  private final PersistableHashMap<String, Set<String>> pStringSetMap =
    new PersistableHashMap<String, Set<String>>(new PersistableString(),
      new PersistableHashSet<String>(new PersistableString()));

  public void saveTo(Store store) {
    pStringBooleanMap.set(myStatusOpenMap);
    StoreUtils.storePersistable(store, STATUS_OPEN_MAP_STID, pStringBooleanMap);
    pStringBooleanMap.clear();

    pStringSetMap.set(myWorkflowTransitionMap);
    StoreUtils.storePersistable(store, WORKFLOW_TRANSITION_MAP_STID, pStringSetMap);
    pStringSetMap.clear();

    pBoolean.set(myDuplicateStatus != null);
    StoreUtils.storePersistable(store, DUP_STATUS_NN_STID, pBoolean);
    if (myDuplicateStatus != null) {
      pString.set(myDuplicateStatus);
      StoreUtils.storePersistable(store, DUP_STATUS_STID_, pString);
    }
    pBoolean.clear();
    pString.clear();
  }

  public void loadFrom(Store store) {
    if (StoreUtils.restorePersistable(store, STATUS_OPEN_MAP_STID, pStringBooleanMap)) {
      myStatusOpenMap.clear();
      myStatusOpenMap.putAll(pStringBooleanMap.copy());
    } else {
      Log.warn(this + ": cannot restore SOM");
      return;
    }

    if (StoreUtils.restorePersistable(store, WORKFLOW_TRANSITION_MAP_STID, pStringSetMap)) {
      myWorkflowTransitionMap.clear();
      myWorkflowTransitionMap.putAll(pStringSetMap.copy());
    } else {
      Log.warn(this + ": cannot restore WTM");
      return;
    }

    if (StoreUtils.restorePersistable(store, DUP_STATUS_NN_STID, pBoolean)) {
      Boolean b = pBoolean.access();
      if (b != null && b) {
        if (StoreUtils.restorePersistable(store, DUP_STATUS_STID_, pString)) {
          myDuplicateStatus = pString.copy();
        } else {
          Log.warn(this + ": cannot restore DS");
          return;
        }
      }
    } else {
      Log.warn(this + ": cannot restore DS");
      return;
    }
  }

  @Override
  public String toString() {
    return "WS@" + hashCode();
  }

  public Boolean isOpen(String status) {
    return myStatusOpenMap.get(status);
  }
}
