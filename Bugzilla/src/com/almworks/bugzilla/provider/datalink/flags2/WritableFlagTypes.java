package com.almworks.bugzilla.provider.datalink.flags2;

import org.jetbrains.annotations.*;

import java.util.List;

class WritableFlagTypes extends FlagTypes {
  private final WriteAccess myAccess;

  public WritableFlagTypes(List<LoadedFlagType> types, WriteAccess access) {
    super(types);
    myAccess = access;
  }

  public static WritableFlagTypes getInstance(WriteAccess access) {
    FlagTypes existing = getExisting(access.getReader());
    if (existing instanceof WritableFlagTypes) return (WritableFlagTypes) existing;
    List<LoadedFlagType> types = existing != null ? existing.getTypes() : loadAll(access);
    WritableFlagTypes instance = new WritableFlagTypes(types, access);
    replaceInstance(access.getReader(), existing, instance);
    return instance;
  }

  public void createOrUpdate(int typeId, String name, Boolean allowsRequestee, String description, char[] allStatuses) {
    assert typeId > 0;
    LoadedFlagType type = findOrCreate(typeId, name);
    type.updateTypeId(myAccess, typeId);
    type.updateStatuses(myAccess, allStatuses, -1);
    type.updateDescription(myAccess, description);
    type.updateName(myAccess, name);
    type.updateSpecRequestable(myAccess, allowsRequestee);
  }

  @NotNull
  public LoadedFlagType findOrCreate(int typeId, String name) {
    LoadedFlagType type = findByTypeId(typeId);
    if (type == null && name != null) {
      type = findByName(name);
      if (type != null && type.getTypeId() > 0 && typeId > 0) type = null;
    }
    if (type == null) {
      type = LoadedFlagType.createNew(myAccess, name);
      addType(type);
    }
    return type;
  }

  public WriteAccess getWriteAccess() {
    return myAccess;
  }
}
