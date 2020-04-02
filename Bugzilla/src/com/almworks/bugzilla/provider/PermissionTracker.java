package com.almworks.bugzilla.provider;

import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.util.Env;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import org.jetbrains.annotations.*;

public class PermissionTracker {
  private static final String FORCE_TIME_TRACKING_ALLOWED = "dz.timetracking";
  private static final String TIME_TRACKING_STORE = "TTA";
  private static final String TIME_TRACKING_ALLOWED_KNOWN = "TTAK";
  private static final String TIME_TRACKING_ALLOWED = "TTA";
  private static final String SEE_ALSO_ALLOWED_KNOWN = "SAAK";
  private static final String SEE_ALSO_ALLOWED = "SAA";

  private final Store myStore;
  private final SimpleModifiable myModifiable = new SimpleModifiable();
  private final Object myLock = new Object();

  @Nullable
  private Boolean myTimeTrackingAllowed;

  @Nullable
  private Boolean mySeeAlsoEnabled;

  public PermissionTracker(Store store) {
    myStore = store;
  }

  public void clearState() {
    clearTimeTrackingAllowed();
  }

  public void start() {
    load();
  }

  // todo extract class for triad-value
  private void load() {
    boolean ttKnown = StoreUtils.restoreBoolean(myStore, TIME_TRACKING_STORE, TIME_TRACKING_ALLOWED_KNOWN);
    Boolean ttAllowed = ttKnown ? StoreUtils.restoreBoolean(myStore, TIME_TRACKING_STORE, TIME_TRACKING_ALLOWED) : null;
    boolean saKnown = StoreUtils.restoreBoolean(myStore, TIME_TRACKING_STORE, SEE_ALSO_ALLOWED_KNOWN);
    Boolean saAllowed = saKnown ? StoreUtils.restoreBoolean(myStore, TIME_TRACKING_STORE, SEE_ALSO_ALLOWED) : null;
    synchronized (myLock) {
      myTimeTrackingAllowed = ttAllowed;
      mySeeAlsoEnabled = saAllowed;
    }
  }

  private void save() {
    saveTriad(myTimeTrackingAllowed, TIME_TRACKING_ALLOWED_KNOWN, TIME_TRACKING_ALLOWED);
    saveTriad(mySeeAlsoEnabled, SEE_ALSO_ALLOWED_KNOWN, SEE_ALSO_ALLOWED);
  }

  private void saveTriad(Boolean value, String knownKey, String valueKey) {
    boolean known = value != null;
    StoreUtils.storeBoolean(myStore, TIME_TRACKING_STORE, knownKey, known);
    StoreUtils.storeBoolean(myStore, TIME_TRACKING_STORE, valueKey, known && value);
  }

  public void setTimeTrackingAllowed(boolean timeTrackingAllowed) {
    synchronized (myLock) {
      if (myTimeTrackingAllowed != null && myTimeTrackingAllowed == timeTrackingAllowed)
        return;
      myTimeTrackingAllowed = timeTrackingAllowed;
    }
    myModifiable.fireChanged();
    save();
  }

  public void clearTimeTrackingAllowed() {
    synchronized (myLock) {
      if (myTimeTrackingAllowed == null)
        return;
      myTimeTrackingAllowed = null;
    }
    myModifiable.fireChanged();
    save();
  }

  /**
   * Returns whether time tracking is allowed for this user, or null if not known.
   */
  @Nullable
  public Boolean isTimeTrackingAllowed() {
    String forceSetting = Env.getString(FORCE_TIME_TRACKING_ALLOWED);
    if (forceSetting != null) {
      if ("true".equalsIgnoreCase(forceSetting)) return true;
      if ("false".equalsIgnoreCase(forceSetting)) return false;
    }
    synchronized (myLock) {
      return myTimeTrackingAllowed;
    }
  }

  public Modifiable getModifiable() {
    return myModifiable;
  }

  public void setSeeAlsoEnabled(boolean seeAlsoEnabled) {
    synchronized (myLock) {
      if (mySeeAlsoEnabled != null && mySeeAlsoEnabled == seeAlsoEnabled)
        return;
      mySeeAlsoEnabled = seeAlsoEnabled;
    }
    myModifiable.fireChanged();
    save();
  }


  /**
   * @return whether "see also" field has been detected as enabled
   */
  @Nullable
  public Boolean isSeeAlsoEnabled() {
    return mySeeAlsoEnabled;
  }
}
