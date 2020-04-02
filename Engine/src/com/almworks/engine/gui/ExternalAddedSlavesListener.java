package com.almworks.engine.gui;

import com.almworks.api.application.*;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertors;
import com.almworks.util.collections.LongSet;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Log;
import org.almworks.util.StringUtil;
import org.almworks.util.detach.Lifespan;

import java.awt.*;
import java.util.Collection;
import java.util.List;

import static org.almworks.util.Collections15.arrayList;

public class ExternalAddedSlavesListener<T> implements DBLiveQuery.Listener {
  private final LongList myCurSlaves;
  private final EditControl myParentEdit;
  private final ModelMap myModel;
  private final ModelKey<List<T>> myModelKey;
  private final long myBugItem;
  private final LoadedItemServices myLis;

  private final Long lastCurSlave;
  private final Long firstCurSlave;

  public ExternalAddedSlavesListener(LongList curSlaves, EditControl parentEdit, ModelMap model, long bugItem, LoadedItemServices lis, ModelKey<List<T>> modelKey) {
    myCurSlaves = curSlaves;
    myParentEdit = parentEdit;
    myModel = model;
    myBugItem = bugItem;
    myLis = lis;
    myModelKey = modelKey;
    lastCurSlave = myCurSlaves.isEmpty() ? null : myCurSlaves.get(myCurSlaves.size() - 1);
    firstCurSlave = myCurSlaves.isEmpty() ? null : myCurSlaves.get(0);
  }

  public static <T extends UiItem> void attach(Lifespan life, ModelMap model, Component component, ModelKey<? extends Collection<T>> key, DBAttribute<Long> masterAttr) {
    // edit lifecycle of the editor which listens to external edits
    EditLifecycle editLife = model.getService(EditLifecycle.SERVICE_KEY);
    LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(model);
    if (editLife == null || lis == null) {
      Log.warn("EASL: cannot add syncman listener " + StringUtil.implode(Convertors.TO_STRING.collectList(arrayList(editLife, lis)), " "));
      assert false;
      return;
    }

    final EditControl parentEdit = editLife.getControl();
    if (parentEdit == null) {
      // nothing is locked: nowhere to add the added slaves, so do nothing
      return;
    }
    long bugItem = lis.getItem();

    LongList curSlaves = LongSet.collect(UiItem.GET_ITEM, key.getValue(model));
    BoolExpr<DP> bugSlavesQuery = DPEquals.create(masterAttr, bugItem);
    Database.require().liveQuery(life, bugSlavesQuery, new ExternalAddedSlavesListener(curSlaves, parentEdit, model, bugItem, lis, key));
  }

  @Override
  public void onICNPassed(long icn) {
  }

  @Override
  public void onDatabaseChanged(DBEvent event, DBReader reader) {
    WritableLongList added = null;
    for (LongListIterator i = event.getAddedAndChangedSorted().iterator(); i.hasNext();) {
      long slave = i.next();
      if (greater(slave, lastCurSlave) || greater(firstCurSlave, slave) || myCurSlaves.binarySearch(slave) < 0) {
        if (added == null) added = new LongArray();
        added.add(slave);
      }
    }
    if (added != null) addSlavesToLockAndReload(added);
  }

  private static boolean greater(Long a, Long b) {
    return (a == null || b == null) ? true : a < b;
  }

  private void addSlavesToLockAndReload(LongList added) {
    myParentEdit.include(added, new EditorFactory() {
      @Override
      public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) throws DBOperationCancelledException {
        final PropertyMap pmap = new PropertyMap();
        myModelKey.extractValue(SyncUtils.readTrunk(reader, myBugItem), myLis, pmap);
        ThreadGate.AWT_QUEUED.execute(new Runnable() {
          @Override
          public void run() {
            myModelKey.copyValue(myModel, pmap);
          }
        });
        return ItemEditor.STUB;
      }
    });
  }
}
