package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class FlagTypes {
  private static final TypedKey<FlagTypes> KEY = TypedKey.create("flagTypes");
  private final List<LoadedFlagType> myTypes;

  public FlagTypes(List<LoadedFlagType> types) {
    myTypes = Collections15.arrayList(types);
  }

  @NotNull
  public static FlagTypes getInstance(ItemVersion bug) {
    FlagTypes types = getExisting(bug.getReader());
    if (types == null) {
      ReadAccess reader = ReadAccess.getInstance(bug);
      types = new FlagTypes(loadAll(reader));
      replaceInstance(bug.getReader(), null, types);
    }
    return types;
  }

  static List<LoadedFlagType> loadAll(ReadAccess access) {
    List<LoadedFlagType> result = Collections15.arrayList();
    LongList types = access.findAllOfType(Flags.KIND_TYPE);
    for (int i = 0; i < types.size(); i++) {
      ItemVersion type = access.read(types.get(i));
      LoadedFlagType loaded = LoadedFlagType.load(type);
      if (loaded != null) result.add(loaded);
    }
    return result;
  }

  @Nullable
  protected static FlagTypes getExisting(DBReader reader) {
    Map cache = reader.getTransactionCache();
    return KEY.getFrom(cache);
  }

  protected static void replaceInstance(DBReader reader, FlagTypes old, FlagTypes replacement) {
    if (replacement == null) return;
    if (getExisting(reader) != old) Log.error("Wrong replacement " + old + " " + replacement);
    KEY.putTo(reader.getTransactionCache(), replacement);
  }

  public List<LoadedFlagType> getTypes() {
    return Collections.unmodifiableList(myTypes);
  }

  @Nullable
  protected LoadedFlagType findByName(String name) {
    List<LoadedFlagType> types = findAllByName(name);
    if (types.isEmpty()) return null;
    LoadedFlagType first = types.get(0);
    if (types.size() == 1) return first;
    types = LoadedFlagType.selectWithTypeId(types);
    return types.isEmpty() ? first : types.get(0);
  }

  @NotNull
  protected List<LoadedFlagType> findAllByName(String name) {
    List<LoadedFlagType> result = Collections15.arrayList();
    for (LoadedFlagType type : myTypes) {
      if (name.equals(type.getName())) result.add(type);
    }
    return result;
  }

  @Nullable
  protected LoadedFlagType findByTypeId(int typeId) {
    if (typeId < 0) return null;
    for (LoadedFlagType type : myTypes) if (typeId == type.getTypeId()) return type;
    return null;
  }

  protected void addType(LoadedFlagType type) {
    myTypes.add(type);
  }

  public LoadedFlagType findByItem(long typeItem) {
    for (LoadedFlagType type : myTypes) {
      if (type.getItem() == typeItem) return type;
    }
    Log.error("No type found for " + typeItem);
    return null;
  }
}
