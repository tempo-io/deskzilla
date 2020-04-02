package com.almworks.items.sync.edit;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.api.DBWriter;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.collections.LongSet;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

abstract class BaseEditDrain extends BaseDBDrain implements EditDrain {
  @Nullable
  private final EditorLock myLock;
  private final LongSet myJustCreated = new LongSet();

  public BaseEditDrain(SyncManagerImpl manager, @Nullable EditorLock lock) {
    super(manager, Branch.TRUNK);
    myLock = lock;
  }

  @Override
  public ItemVersionCreator createItem() {
    ItemVersionCreator item = super.createItem();
    myJustCreated.add(item.getItem());
    return item;
  }

  @Override
  public void beforeShadowableChanged(long item, boolean isNew) {
    super.beforeShadowableChanged(item, isNew);
    HolderCache holders = HolderCache.instance(getWriter());
    AttributeMap base;
    if (myJustCreated.contains(item)) {
      assert isNew : item;
      assert holders.getBase(item) == null;
      base = SyncSchema.getInvisible();
      myJustCreated.remove(item);
    } else {
      assert !isNew : item;
      onBeforeShadowableChanged(item);
      if (holders.getBase(item) != null) return;
      base = getBase(item);
    }
    if (base == null) Log.error("Missing bases while changing item " + item);
    else holders.setBase(item, base);
  }

  protected void onBeforeShadowableChanged(long item) {}

  /**
   * Called when shadowable attribute of the item is changed for the first time and the item has no base shadow yet
   * (first edit of synchronized item).
   * @param item changed item
   * @return item state before edit was started (In common case - the state that user had seem when open editor)
   */
  @NotNull
  protected abstract AttributeMap getBase(long item);

  @Override
  public ItemVersionCreator markMerged(long item) {
    HolderCache holders = HolderCache.instance(getReader());
    AttributeMap conflict = holders.getConflict(item);
    if (conflict != null) {
      holders.setBase(item, conflict);
      holders.setConflict(item, null);
    }
    return changeItem(item);
  }

  @Override
  public boolean discardChanges(long root) {
    DBWriter writer = getWriter();
    LongList subtree = SyncUtils.getSlavesSubtree(writer, root);
    if (!getManager().lockOrMergeLater(writer, subtree, myLock, LongArray.singleton(root))) {
      // todo: implement discard in any case
      return false;
    }
    LongSet toClear = new LongSet();
    BranchUtil util = BranchUtil.instance(getReader());
    for (int i = 0; i < subtree.size(); i++) {
      long slave = subtree.get(i);
      ItemVersion serverSlave = util.readServerIfExists(slave);
      if (serverSlave == null) continue;
      if (serverSlave.isInvisible()) toClear.add(slave);
    }
    LongList allToClear = SyncUtils.getSlavesSubtrees(writer, toClear);
    for (int i = 0; i < allToClear.size(); i++) writer.clearItem(allToClear.get(i));
    for (int i = 0; i < subtree.size(); i++) {
      long item = subtree.get(i);
      if (allToClear.contains(item)) continue;
      SyncSchema.discardSingle(writer, item);
    }
    return true;
  }

  public static TLongObjectHashMap<AttributeMap> collectBases(DBReader reader, LongList items) {
    TLongObjectHashMap<AttributeMap> result = new TLongObjectHashMap<AttributeMap>();
    collectBases(reader, items, result);
    return result;
  }

  public static void collectBases(DBReader reader, LongList items, TLongObjectHashMap<AttributeMap> result) {
    HolderCache holders = HolderCache.instance(reader);
    for (int i = 0; i < items.size(); i++) {
      long item = items.get(i);
      if (result.containsKey(item)) continue;
      AttributeMap base = holders.getBase(item);
      if (base == null) base = loadBase(reader, item);
      result.put(item, base);
    }
  }

  protected static AttributeMap loadBase(DBReader reader, long item) {
    return SyncUtils.readTrunk(reader, item).getAllShadowableMap();
  }
}
