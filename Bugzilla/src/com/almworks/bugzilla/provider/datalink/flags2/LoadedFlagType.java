package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.collections.LongSet;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class LoadedFlagType implements Comparable<LoadedFlagType> {
  static final char[] ALL_STATUSES = new char[]{'?', '-', '+', 'X'};
  private final long myItem;
  private final long myConnection;
  private final LongSet myPlus = new LongSet();
  private final LongSet myMinus = new LongSet();
  private int myTypeId;
  private String myName;
  private int myFlags;
  private String myDescription;

  private LoadedFlagType(long item, String name, long connection) {
    myItem = item;
    myName = name;
    myConnection = connection;
  }

  public static LoadedFlagType createNew(WriteAccess access, String name) {
    ItemVersionCreator creator = access.create(Flags.KIND_TYPE);
    creator.setValue(Flags.AT_TYPE_NAME, name);
    return new LoadedFlagType(creator.getItem(),name, access.getConnectionItem());
  }

  @Nullable
  public static LoadedFlagType load(ItemVersion type) {
    String name = type.getValue(Flags.AT_TYPE_NAME);
    if (name == null) {
      Log.error("Missing name " + type);
      return null;
    }
    Long connection = type.getValue(SyncAttributes.CONNECTION);
    if (connection == null) {
      Log.error("Missing connection " + connection);
      return null;
    }
    LoadedFlagType loaded = new LoadedFlagType(type.getItem(), name, connection);
    loaded.myDescription = type.getValue(Flags.AT_TYPE_DESCRIPTION);
    loaded.myFlags = type.getNNValue(Flags.AT_TYPE_FLAGS, 0);
    loaded.myTypeId = type.getNNValue(Flags.AT_TYPE_ID, -1);
    loadApplicability(type, loaded.myPlus, Flags.AT_TYPE_APPLICABLE_PLUS);
    loadApplicability(type, loaded.myMinus, Flags.AT_TYPE_APPLICABLE_MINUS);
    return loaded;
  }

  private static void loadApplicability(ItemVersion type, LongSet target, DBAttribute<Set<Long>> attribute) {
    Set<Long> value = type.getValue(attribute);
    if (value == null || value.isEmpty()) return;
    for (Long component : value) 
      target.add(component);
  }

  public int getTypeId() {
    return myTypeId;
  }

  public long getConnection() {
    return myConnection;
  }

  public String getName() {
    return myName;
  }

  public long getItem() {
    return myItem;
  }

  public void updateTypeId(DBDrain drain, int typeId) {
    if (myTypeId > 0) {
      assert myTypeId == typeId;
      return;
    } else if (typeId < 0) return;
    setValue(drain, Flags.AT_TYPE_ID, typeId);
    myTypeId = typeId;
  }

  public void updateStatuses(DBDrain drain, char[] allStatuses, int except) {
    if (allStatuses == null) return;
    int flags = myFlags;
    if (allStatuses.length == 1 && allStatuses[0] == 'X') allStatuses = Const.EMPTY_CHARS;
    for (char status : ALL_STATUSES) {
      if (status == except) continue;
      boolean set = ArrayUtil.indexOf(allStatuses, status) >= 0;
      flags = setFlag(flags, statusFlag(FlagStatus.fromChar(status)), set);
    }
    flags = setFlag(flags, FLG_STATUSES_KNOWN, true);
    if (flags != myFlags) {
      myFlags = flags;
      setValue(drain, Flags.AT_TYPE_FLAGS, flags);
    }
  }

  public void updateDescription(DBDrain drain, String description) {
    if (updateString(drain, Flags.AT_TYPE_DESCRIPTION, myDescription, description)) myDescription = description;
  }

  public void updateName(DBDrain drain, String name) {
    if (updateString(drain, Flags.AT_TYPE_NAME, myName, name)) myName = name;
  }

  public void updateSpecRequestable(DBDrain drain, @Nullable Boolean requestable) {
    if (requestable == null) return;
    if (isSureValue(myFlags, FLG_SPEC_REQUEST, true)) return;
    updateUnsureFlag(drain, FLG_SPEC_REQUEST, requestable);
  }

  private void updateUnsureFlag(DBDrain drain, int firstFlag, boolean newValue) {
    if (!isSureValue(myFlags, firstFlag) || isFlagSet(myFlags, firstFlag + 1) != newValue) {
      int newFlags = myFlags;
      newFlags = setFlag(newFlags, firstFlag, true);
      newFlags = setFlag(newFlags, firstFlag + 1, newValue);
      if (myFlags != newFlags) {
        myFlags = newFlags;
        setValue(drain, Flags.AT_TYPE_FLAGS, myFlags);
      }
    }
  }

  private boolean updateString(DBDrain drain, DBAttribute<String> attribute, String current, String update) {
    if (Util.equals(current, update) || update == null || update.isEmpty()) return false;
    setValue(drain, attribute, update);
    return true;
  }

  private <T> void setValue(DBDrain drain, DBAttribute<T> attribute, T value) {
    drain.changeItem(myItem).setValue(attribute, value);
  }

  public static final int FLG_TYPE_REQUEST = 0; // Has status ?
  public static final int FLG_TYPE_PLUS = 1; // Has status +
  public static final int FLG_TYPE_MINUS = 2; // Has status -
  public static final int FLG_SPEC_REQUEST = 3; // is specifically requestable 2 bits (00=null, 10=false, 11=true)
  public static final int FLG_MULTIPLICABLE = 5; // is multiplicable 2 bits
  public static final int FLG_STATUSES_KNOWN = 8; // all available statuses were once loaded
  public static final int FLG_TYPE_CLEAR = 9; // Has status X (can be cleared)

  public static int setFlag(int bits, int flag, boolean set) {
    if (flag < 0) return bits;
    int mask = 1 << flag;
    return (bits & ~mask) | (set ? mask : 0);
  }

  public static int statusFlag(FlagStatus status) {
    switch (status) {
    case PLUS: return FLG_TYPE_PLUS;
    case MINUS: return FLG_TYPE_MINUS;
    case QUESTION: return FLG_TYPE_REQUEST;
    case UNKNOWN: return FLG_TYPE_CLEAR;
    default:
      Log.error("Wrong status " + status);
      return -1;
    }
  }

  public static boolean isSureValue(int bits, int firstFlag, boolean value) {
    return isFlagSet(bits, firstFlag) && value == isFlagSet(bits, firstFlag + 1);
  }

  public static boolean isFlagSet(int bits, int flag) {
    return flag >= 0 && (bits & (1 << flag)) != 0;
  }

  public static boolean isSureValue(int bits, int firstFlag) {
    return isFlagSet(bits, firstFlag);
  }

  public int compareTo(LoadedFlagType other) {
    if (other == null) return -1;
    return myTypeId - other.myTypeId;
  }

  public String getDescription() {
    return myDescription;
  }

  public static boolean allowsStatus(int flags, FlagStatus status) {
    boolean statusesKnown = isFlagSet(flags, FLG_STATUSES_KNOWN);
    if (!statusesKnown) return true;
    int flagIndex = statusFlag(status);
    return isFlagSet(flags, flagIndex);
  }

  public static boolean isSureSingleton(int flags) {
    return isSureValue(flags, FLG_MULTIPLICABLE, false);
  }

  public static boolean isSureSpecificallyRequestable(int flags, boolean sureValue) {
    return isSureValue(flags, FLG_SPEC_REQUEST, sureValue);
  }

  public int getOptionFlags() {
    return myFlags;
  }

  public void updateComponent(DBDrain drain, long component, boolean applicable) {
    LongSet kind = applicable ? myPlus : myMinus;
    if (kind.contains(component)) return;
    kind.add(component);
    DBAttribute<Set<Long>> attribute = applicable ? Flags.AT_TYPE_APPLICABLE_PLUS : Flags.AT_TYPE_APPLICABLE_MINUS;
    setValue(drain, attribute, kind.toObjectSet());
  }

  public LongList getApplicability(boolean plus) {
    return plus ? myPlus : myMinus;
  }

  public void updateMultiplicable(DBDrain drain, boolean sure) {
    updateUnsureFlag(drain, FLG_MULTIPLICABLE, sure);
  }

  public static List<LoadedFlagType> selectWithTypeId(Collection<LoadedFlagType> types) {
    List<LoadedFlagType> result = Collections15.arrayList();
    for (LoadedFlagType type : types) {
      if (type.getTypeId() > 0) result.add(type);
    }
    return result;
  }
}
