package com.almworks.bugzilla.provider;

import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.bugzilla.integration.data.ComponentDefaults;
import com.almworks.util.io.persist.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.List;
import java.util.Map;

public class ComponentDefaultsTracker {
  private final Store myStore;

  /**
   * maps product to component to component defaults
   */
  private final Map<String, Map<String, ComponentDefaults>> myDefaults = Collections15.hashMap();

  private final PersistableHashMap<String, Map<String, ComponentDefaults>> myPersistable =
    PersistableHashMap.create(new PersistableString(),
      PersistableHashMap.create(new PersistableString(), new ComponentDefaultsPersistable()));

  public ComponentDefaultsTracker(Store store) {
    myStore = store;
  }


  public synchronized void report(String product, Map<String, ComponentDefaults> defaultsMap) {
    if (product == null || defaultsMap == null)
      return;
    Map<String, ComponentDefaults> oldMap = myDefaults.get(product);
    if (oldMap != null && oldMap.equals(defaultsMap))
      return;
    myDefaults.put(product, defaultsMap);
    save();
  }

  @Nullable
  public synchronized ComponentDefaults getDefaults(String product, String component) {
    Map<String, ComponentDefaults> map = myDefaults.get(product);
    if (map == null)
      return null;
    return map.get(component);
  }

  public synchronized void clearState() {
    myDefaults.clear();
    save();
  }

  public synchronized void start() {
    load();
  }

  private void save() {
    assert Thread.holdsLock(this);
    myPersistable.set(myDefaults);
    StoreUtils.storePersistable(myStore, "*", myPersistable);
    myPersistable.clear();
  }

  private void load() {
    assert Thread.holdsLock(this);
    myDefaults.clear();
    myPersistable.clear();
    if (StoreUtils.restorePersistable(myStore, "*", myPersistable)) {
      myDefaults.putAll(myPersistable.copy());
    } else {
      Log.warn(this + ": cannot restore");
    }
    myPersistable.clear();
  }

  @Override
  public String toString() {
    return "CDT";
  }

  private static class ComponentDefaultsPersistable extends LeafPersistable<ComponentDefaults> {
    private final PersistableString myAssignee = new PersistableString();
    private final PersistableString myQA = new PersistableString();
    private final PersistableArrayList<String> myCC = new PersistableArrayList<String>(new PersistableString());
    private boolean myHasCC;

    protected void doClear() {
      myAssignee.clear();
      myQA.clear();
      myCC.clear();
      myHasCC = false;
    }

    protected ComponentDefaults doAccess() {
      return doCopy();
    }

    protected ComponentDefaults doCopy() {
      return new ComponentDefaults(myAssignee.access(), myQA.access(), myHasCC ? myCC.copy() : null);
    }

    protected void doRestore(DataInput in) throws IOException, FormatException {
      myAssignee.restore(in);
      myQA.restore(in);
      myHasCC = in.readBoolean();
      if (myHasCC) {
        myCC.restore(in);
      } else {
        myCC.clear();
      }
    }

    protected void doSet(ComponentDefaults value) {
      myAssignee.set(value.getDefaultAssignee());
      myQA.set(value.getDefaultQA());
      List<String> cc = value.getDefaultCC();
      myHasCC = cc != null;
      if (myHasCC) {
        myCC.set(cc);
      } else {
        myCC.clear();
      }
    }

    protected void doStore(DataOutput out) throws IOException {
      myAssignee.store(out);
      myQA.store(out);
      out.writeBoolean(myHasCC);
      if (myHasCC) {
        myCC.store(out);
      }
    }
  }
}
