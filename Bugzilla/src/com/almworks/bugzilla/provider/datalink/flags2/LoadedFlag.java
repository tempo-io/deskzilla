package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.LoadedObject;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.integers.LongList;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.VersionSource;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.List;

class LoadedFlag {
  private static final LoadedObject.AttributeSet ATTRS = new LoadedObject.AttributeSet(Flags.KIND_FLAG, Flags.AT_FLAG_MASTER)
    .addAttribute(Flags.AT_FLAG_ID, -1)
    .addItemReference(Flags.AT_FLAG_SETTER)
    .addAttribute(Flags.AT_FLAG_STATUS, 'X')
    .addItemReference(Flags.AT_FLAG_REQUESTEE)
    .addItemReference(Flags.AT_FLAG_TYPE)
    .fix();
  private final PrivateMetadata myPm;
  private final LoadedObject.Writable myDBFlag;

  private LoadedFlag(PrivateMetadata pm, LoadedObject.Writable flag) {
    myPm = pm;
    myDBFlag = flag;
  }

  @NotNull
  public static List<LoadedFlag> loadAll(PrivateMetadata pm, ItemVersionCreator bug, boolean includeDeleted) {
    LongList flags = bug.getSlaves(Flags.AT_FLAG_MASTER);
    List<LoadedFlag> result = Collections15.arrayList();
    for (int i = 0; i < flags.size(); i++) {
      ItemVersionCreator flag = bug.changeItem(flags.get(i));
      if (!includeDeleted && flag.isInvisible()) continue;
      LoadedFlag loaded = new LoadedFlag(pm, ATTRS.changeServer(flag));
      result.add(loaded);
    }
    return result;
  }

  @Nullable
  public static LoadedFlag findById(List<LoadedFlag> flags, int flagId) {
    if (flagId < 0) return null;
    for (LoadedFlag flag : flags) if (flagId == flag.getId()) return flag;
    return null;
  }

  private int getId() {
    return myDBFlag.getValue(Flags.AT_FLAG_ID);
  }

  public static LoadedFlag createNew(WriteAccess access, int typeId, String name, ItemVersionCreator bug) {
    WritableFlagTypes types = WritableFlagTypes.getInstance(access);
    LoadedFlagType type = types.findOrCreate(typeId, name);
    LoadedObject.Writable item = ATTRS.newSlave(bug);
    item.setValue(Flags.AT_FLAG_TYPE, type.getItem());
    return new LoadedFlag(access.getPrivateMD(), item);
  }

  public void update(BugzillaUser setterUser, char status, BugzillaUser requesteeId, int flagId) {
    long setter = getOrCreateUser(setterUser);
    long requestee = getOrCreateUser(requesteeId);
    assert status != 'X';
    myDBFlag.setAlive();
    if (setter > 0) myDBFlag.setValue(Flags.AT_FLAG_SETTER, setter);
    myDBFlag.setValue(Flags.AT_FLAG_REQUESTEE, requestee > 0 ? requestee : null);
    myDBFlag.setValue(Flags.AT_FLAG_STATUS, status);
    int currentId = getId();
    if (currentId < 0 && flagId >= 0) myDBFlag.setValue(Flags.AT_FLAG_ID, flagId);
    else if (currentId >= 0 && flagId >= 0 && currentId != flagId) Log.error("Different ids " + currentId + ' ' + flagId);
  }

  private long getOrCreateUser(BugzillaUser user) {
    return User.getOrCreate(myDBFlag.getDrain(), user, myPm);
  }

  public void delete() {
    myDBFlag.delete();
  }

  public String getName(FlagTypes types) {
    return types.findByItem(getTypeItem()).getName();
  }

  public boolean isDeleted() {
    return myDBFlag.isDeleted();
  }

  public long getTypeItem() {
    return myDBFlag.getValue(Flags.AT_FLAG_TYPE);
  }

  public static List<LoadedFlag> selectWithId(List<LoadedFlag> flags) {
    List<LoadedFlag> result = Collections15.arrayList();
    for (LoadedFlag flag : flags) if (flag.getId() >= 0) result.add(flag);
    return result;
  }

  public boolean matchesState(BugzillaUser setter, char status, BugzillaUser requestee) {
    return status == myDBFlag.getValue(Flags.AT_FLAG_STATUS)
      && matchUser(myPm, myDBFlag.getDrain(), myDBFlag.getValue(Flags.AT_FLAG_REQUESTEE), requestee)
      && matchUser(myPm, myDBFlag.getDrain(), myDBFlag.getValue(Flags.AT_FLAG_SETTER), setter);
  }

  private static boolean matchUser(PrivateMetadata pm, VersionSource source, long user, BugzillaUser id) {
    if (id == null) {
      if (user > 0) return false;
    } else {
      return user > 0 && User.equalId(source.forItem(user), id);
    }
    return true;
  }
}
