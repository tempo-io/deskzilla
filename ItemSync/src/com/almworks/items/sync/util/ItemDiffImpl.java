package com.almworks.items.sync.util;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.util.AttributeMap;
import com.almworks.items.util.DatabaseUtil;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;

public class ItemDiffImpl implements ModifiableDiff {
  private List<byte[]> myUpdatedHistory = null;
  private final Set<DBAttribute<?>> myChanges = Collections15.hashSet();
  @Nullable
  private final byte[][] myInitialHistory;
  private final ItemVersion myElder;
  private final ItemVersion myNewer;

  private ItemDiffImpl(ItemVersion elder, ItemVersion newer, byte[][] initialHistory) {
    myInitialHistory = initialHistory != null && initialHistory.length > 0 ? initialHistory : null;
    myElder = elder;
    myNewer = newer;
  }

  public long getItem() {
    return myNewer.getItem();
  }

  public boolean hasChanges() {
    boolean hasHistory = hasHistory();
    return hasHistory || !myChanges.isEmpty();
  }

  @Override
  public boolean hasHistory() {
    return myUpdatedHistory == null ? myInitialHistory != null : !myUpdatedHistory.isEmpty();
  }

  @Override
  public ItemVersion getNewerVersion() {
    return myNewer;
  }

  @Override
  public ItemVersion getElderVersion() {
    return myElder;
  }

  public Collection<? extends DBAttribute<?>> getChanged() {
    return Collections.unmodifiableCollection(myChanges);
  }

  public DBReader getReader() {
    return myNewer.getReader();
  }

  public <T> T getNewerValue(DBAttribute<? extends T> attribute) {
    return myNewer.getValue(attribute);
  }

  @Override
  public <T> T getElderValue(DBAttribute<? extends T> attribute) {
    return myElder.getValue(attribute);
  }

  public boolean isChanged(DBAttribute<?> attribute) {
    return myChanges.contains(attribute);
  }

  /**
   * @return null means no changes made to TRUNK. if Old is null this means TRUNK has no changes
   */
  @Nullable
  public static ItemDiffImpl createToTrunk(DBReader reader, long item, @Nullable ItemVersion old) {
    if (old == null) return null;
    ItemVersion trunk = SyncUtils.readTrunk(reader, item);
    return create(old, trunk);
  }

  private static byte[][] getHistoryDiff(ItemVersion older, ItemVersion newer) {
    byte[][] oldHistory = SyncSchema.getHistory(older);
    byte[][] newHistory = SyncSchema.getHistory(newer);
    if (oldHistory.length == 0) return newHistory;
    if (oldHistory.length > newHistory.length)
      Log.error("Wrong history: " + ArrayUtil.toString(oldHistory) + " " + ArrayUtil.toString(newHistory));
    int offset = oldHistory.length;
    for (int i = 0; i < Math.min(oldHistory.length, newHistory.length); i++) {
      byte[] bytes = oldHistory[i];
      if (!Arrays.equals(bytes, newHistory[i])) {
        Log.error("Steps not equal at " + i + " " + ArrayUtil.toString(oldHistory) + " " + ArrayUtil.toString(newHistory));
        offset = i;
        break;
      }
    }
    return offset == newHistory.length ? Const.EMPTY_BYTES2D :
      ArrayUtil.arrayCopy(newHistory, offset, newHistory.length - offset);
  }

  @NotNull
  public static ItemDiffImpl create(ItemVersion elder, ItemVersion newer) {
    if (elder == null && newer == null) throw new NullPointerException();
    if (elder == null || newer == null) {
      Log.error("Some version is null " + elder + " " + newer);
      ItemVersion nn = elder == null ? newer : elder;
      return new ItemDiffImpl(nn, nn, SyncSchema.getHistory(nn));
    }
    long item = newer.getItem();
    if (elder.getItem() != item) Log.error("Comparing different items " + elder + " " + newer);
    byte[][] history = getHistoryDiff(elder, newer);
    ItemDiffImpl diff = new ItemDiffImpl(elder, newer, history);
    AttributeMap elderValues = elder.getAllShadowableMap();
    AttributeMap newerValues = newer.getAllShadowableMap();
    collectChanges(diff.getReader(), elderValues, newerValues, diff.myChanges);
    return diff;
  }

  public static void collectChanges(DBReader reader, AttributeMap values1, AttributeMap values2, Collection<? super DBAttribute<?>> target) {
    for (DBAttribute<?> attribute : values1.keySet())
      if (!isEqualValueInMap(reader, attribute, values1, values2)) target.add(attribute);
    for (DBAttribute<?> attribute : values2.keySet()) {
      if (!target.contains(attribute) && !isEqualValueInMap(reader, attribute, values1, values2))
        target.add(attribute);
    }
  }

  public static ItemDiffImpl createNoChange(ItemVersion version) {
    return new ItemDiffImpl(version, version, SyncSchema.getHistory(version));
  }

  /**
   * @return null if history not updated otherwise not null. Empty array for update to empty history
   */
  @Nullable
  public List<byte[]> getUpdatedHistory() {
    return myUpdatedHistory != null ? Collections.unmodifiableList(myUpdatedHistory) : null;
  }

  public void clearHistory() {
    if (myUpdatedHistory == null) myUpdatedHistory = Collections15.arrayList();
    myUpdatedHistory.clear();
  }

  @Override
  public void addChange(DBAttribute<?>... attributes) {
    myChanges.addAll(Arrays.asList(attributes));
  }

  public static <T> boolean isEqualNewer(ItemDiff diff1, ItemDiff diff2, DBAttribute<T> attribute) {
    T val1 = diff1.getNewerValue(attribute);
    T val2 = diff2.getNewerValue(attribute);
    return isEqualValue(diff1.getReader(), attribute, val1, val2);
  }


  public static <T> boolean isEqualValueInMap(DBReader reader, DBAttribute<T> attribute, AttributeMap map1, AttributeMap map2) {
    T value1 = map1 != null ? map1.get(attribute) : null;
    T value2 = map2 != null ? map2.get(attribute) : null;
    return isEqualValue(reader, attribute, value1, value2);
  }

  public static <T> boolean isEqualValue(DBReader reader, DBAttribute<T> attribute, T value1, T value2) {
    if (DatabaseUtil.isEqualValue(attribute, value1, value2)) return true;
    DBAttribute.ScalarComposition composition = attribute.getComposition();
    if (composition != DBAttribute.ScalarComposition.SCALAR) return false;
    if (attribute.getScalarClass() != BigDecimal.class) return false;
    DBAttribute<BigDecimal> decAttr = (DBAttribute<BigDecimal>) attribute;
    BigDecimal dec1 = (BigDecimal) value1;
    BigDecimal dec2 = (BigDecimal) value2;
    return isDecimalEquals(reader, decAttr, dec1, dec2);
  }

  private static boolean isDecimalEquals(DBReader reader, DBAttribute<BigDecimal> attribute, BigDecimal value1, BigDecimal value2) {
    if (Util.equals(value1, value2)) return true;
    if (value1 == null || value2 == null) return false;
    long attr = reader.findMaterialized(attribute);
    Integer scale = reader.getValue(attr, SyncSchema.DECIMAL_SCALE);
    if (scale == null) return false;
    int scale1 = value1.scale();
    int scale2 = value2.scale();
    if (scale1 == scale2 && scale >= scale1) return false;
    int commonScale = Math.min(scale, Math.max(scale1, scale2));
    BigDecimal v1 = value1.setScale(commonScale, BigDecimal.ROUND_HALF_UP);
    BigDecimal v2 = value2.setScale(commonScale, BigDecimal.ROUND_HALF_UP);
    return v1.equals(v2);
  }
}
