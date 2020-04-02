package com.almworks.items.sync.edit;

import com.almworks.items.api.*;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.impl.HolderCache;
import com.almworks.items.sync.impl.VersionHolder;
import com.almworks.items.sync.util.ItemDiffImpl;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.exec.ThreadGate;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;

class UnsafeCommitDrain extends BaseEditDrain {
  private final EditCommit myCommit;
  private final Map<EditCounterpart, TLongObjectHashMap<AttributeMap>> myChanges = Collections15.hashMap();
  private Map<EditCounterpart, TLongObjectHashMap<ItemValues>> myNewValues = null;

  UnsafeCommitDrain(SyncManagerImpl manager, EditCommit commit) {
    super(manager, null);
    myCommit = commit;
  }

  @Override
  protected void performTransaction() throws DBOperationCancelledException {
    myCommit.performCommit(this);
    collectChanges();
  }

  private void collectChanges() {
    assert myNewValues == null;
    List<DBAttribute<?>> changedAttrs = Collections15.arrayList();
    Map<EditCounterpart, TLongObjectHashMap<ItemValues>> newValuesMap = Collections15.hashMap();
    for (Map.Entry<EditCounterpart, TLongObjectHashMap<AttributeMap>> entry : myChanges.entrySet()) {
      TLongObjectHashMap<AttributeMap> changedItems = entry.getValue();
      TLongObjectHashMap<ItemValues> newValues = new TLongObjectHashMap<>();
      for (TLongObjectIterator<AttributeMap> it = changedItems.iterator(); it.hasNext();) {
        it.advance();
        AttributeMap original = it.value();
        long item = it.key();
        AttributeMap values = getTrunkValues(item);
        ItemValues itemDiff;
        if (values == null) itemDiff = null;
        else {
          changedAttrs.clear();
          ItemDiffImpl.collectChanges(getReader(), original, values, changedAttrs);
          if (changedAttrs.isEmpty()) itemDiff = null;
          else itemDiff = ItemValues.collect(changedAttrs, values);
        }
        if (itemDiff != null) newValues.put(item, itemDiff);
      }
      newValuesMap.put(entry.getKey(), newValues);
    }
    myChanges.clear();
    myNewValues = newValuesMap;
  }

  @Override
  protected void onTransactionFinished(DBResult<?> result) {
    myCommit.onCommitFinished(result.isSuccessful());
    final Map<EditCounterpart, TLongObjectHashMap<ItemValues>> newValues = myNewValues;
    if (newValues != null)
      ThreadGate.AWT.execute(new Runnable() {
        @Override
        public void run() {
          for (Map.Entry<EditCounterpart, TLongObjectHashMap<ItemValues>> entry : newValues.entrySet()) {
            entry.getKey().notifyConcurrentEdit(entry.getValue());
          }
        }
      });
  }

  @NotNull
  @Override
  protected AttributeMap getBase(long item) {
    EditCounterpart prev = null;
    while (true) {
      EditCounterpart lock = getManager().findLock(item);
      if (lock == null || !lock.isAlive()) break;
      if (lock == prev) {
        Log.error("Can not get base from " + lock + " (" + item + ")");
        break;
      }
      AttributeMap base = lock.getItemBase(item);
      if (base != null) return base;
      prev = lock;
    }
    return loadBase(getReader(), item);
  }

  @Override
  protected void onBeforeShadowableChanged(long item) {
    EditCounterpart counterpart = getManager().findLock(item);
    if (counterpart == null) return;
    TLongObjectHashMap<AttributeMap> changes = myChanges.get(counterpart);
    if (changes == null) {
      changes = new TLongObjectHashMap<>();
      myChanges.put(counterpart, changes);
    }
    AttributeMap trunkValues = getTrunkValues(item);
    if (trunkValues == null) return;
    changes.put(item, trunkValues);
  }

  private AttributeMap getTrunkValues(long item) {
    VersionHolder trunk = HolderCache.instance(getWriter()).getHolder(item, null, false);
    if (trunk == null) return null;
    return trunk.getAllShadowableMap();
  }
}
