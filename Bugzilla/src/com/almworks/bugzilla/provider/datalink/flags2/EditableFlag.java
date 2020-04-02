package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.ItemKey;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.sync.SyncState;
import com.almworks.util.collections.Convertor;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.Comparator;
import java.util.List;

public class EditableFlag implements Flag {
  public static final Comparator<? super EditableFlag> TYPE_ORDER = new Comparator<EditableFlag>() {
    @Override
    public int compare(EditableFlag o1, EditableFlag o2) {
      FlagTypeItem t1 = getType(o1);
      FlagTypeItem t2 = getType(o2);
      return ItemKey.COMPARATOR.compare(t1, t2);
    }

    @Nullable
    private FlagTypeItem getType(EditableFlag flag) {
      return flag != null ? flag.getType() : null;
    }
  };

  public static final Convertor<EditableFlag, String> TYPE_NAME = new Convertor<EditableFlag, String>() {
    @Override
    public String convert(EditableFlag value) {
      FlagTypeItem type = value != null ? value.getType() : null;
      return type != null ? type.getDisplayName() : "";
    }
  };

  public static final Convertor<EditableFlag, String> SETTER_DISPLAY = new Convertor<EditableFlag, String>() {
    @Override
    public String convert(EditableFlag value) {
      String setter = value.mySetter == null ? null : value.mySetter.getDisplayName();
      return setter != null && setter.length() > 0 ? setter + ":" : setter;
    }
  };

  @Nullable
  private final FlagVersion myDBVersion;
  private final UIFlagData myData;
  private final long myType;
  private FlagStatus myStatus;
  private ItemKey myRequestee;
  private ItemKey mySetter;
  private boolean myDeleted;

  private EditableFlag(UIFlagData data, long type, FlagVersion flag) {
    myData = data;
    myDBVersion = flag;
    myType = type;
    reset();
  }

  private EditableFlag(UIFlagData data, long type, ItemKey setter, FlagStatus status) {
    myData = data;
    myType = type;
    mySetter = setter;
    myStatus = status;
    myDeleted = false;
    myDBVersion = null;
  }

  private void reset() {
    reset(myDBVersion);
  }

  private void reset(FlagVersion version) {
    if (version == null) return;
    myStatus = version.getStatus();
    myRequestee = version.getRequesteeKey();
    mySetter = version.getSetterKey();
    myDeleted = version.isDeleted();
  }

  @NotNull
  public static List<EditableFlag> toEditableList(List<FlagVersion> allFlags) {
    if (allFlags == null || allFlags.isEmpty()) return Collections15.arrayList();
    UIFlagData data = allFlags.get(0).getData();
    List<EditableFlag> edit = Collections15.arrayList(allFlags.size());
    for (FlagVersion flag : allFlags) {
      if (flag != null) edit.add(new EditableFlag(data, flag.getTypeItem(), flag));
      else Log.error("null flag");
    }
    return edit;
  }

  public static EditableFlag createNew(FlagTypeItem type, FlagStatus status, BugzillaConnection connection) {
    UIFlagData data = type.getData();
    ItemKey thisUser;
    if (connection != null) {
      PrivateMetadata pm = connection.getContext().getPrivateMetadata();
      long thisUserItem = pm.getThisUser();
      thisUser = thisUserItem > 0 ? data.getUserKey(thisUserItem) : null;
    } else {
      Log.error("Missing connection");
      thisUser = null;
    }
    return new EditableFlag(data, type.getResolvedItem(), thisUser, status);
  }

  @Nullable
  public FlagTypeItem getType() {
    return myData.getTypeKey(myType);
  }

  public boolean discard() {
    boolean local = myDBVersion == null;
    if (myDBVersion != null && myDBVersion.getSyncState().isLocalOnly()) {
      myStatus = FlagStatus.UNKNOWN;
      myDeleted = true;
    } else reset(getOriginalVersion());
    return local;
  }

  public EditableFlag createCopy() {
    EditableFlag copy = new EditableFlag(myData, myType, myDBVersion);
    copy.setStatus(getStatus());
    copy.setRequestee(getRawRequestee());
    return copy;
  }

  @Override
  public String getName() {
    return myData.getTypeName(myType);
  }

  @Override
  public String getSetterName() {
    return mySetter != null ? mySetter.getDisplayName() : "";
  }

  @Override
  public String getRequesteeName() {
    ItemKey user = getRequestee();
    return user != null ? user.getDisplayName() : "";
  }

  public boolean isLocalOnly() {
    return myDBVersion == null || myDBVersion.getSyncState().isLocalOnly();
  }

  public boolean setStatus(FlagStatus newStatus) {
    if (newStatus == null) return false;
    boolean newDeleted = newStatus == FlagStatus.UNKNOWN;
    boolean changed = myStatus != newStatus || myDeleted != newDeleted;
    myStatus = newStatus;
    myDeleted = newDeleted;
    return changed;
  }

  public boolean setRequestee(ItemKey requestee) {
    if (Util.equals(getRawRequestee(), requestee)) return false;
    myRequestee = requestee;
    return true;
  }

  public FlagStatus getStatus() {
    if (myDeleted) {
      FlagVersion prevVersion = getOriginalVersion();
      if (prevVersion != null) return prevVersion.getStatus();
    }
    return myStatus;
  }

  @Nullable
  public ItemKey getRequestee() {
    FlagStatus status = getStatus();
    return status == FlagStatus.QUESTION ? getRawRequestee() : null;
  }

  public ItemKey getRawRequestee() {
    ItemKey requestee = myRequestee;
    if (requestee != null && requestee.getResolvedItem() <= 0) {
      if (ItemKey.INVALID.equals(requestee)) return null;
      String id = requestee.getId();
      if (id.trim().length() == 0) return null;
    }
    return requestee;
  }

  public boolean isEdited() {
    return myDBVersion == null || !isEqualState(myDBVersion, getStatus(), getRequestee(), myDeleted);
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  private boolean isEqualState(FlagVersion version, FlagStatus status, ItemKey requestee, boolean deleted) {
    return version.isEqualState(status.getChar(), requestee != null ? requestee.getResolvedItem() : 0, deleted);
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public SyncState getSyncState() {
    if (myDBVersion == null) return SyncState.NEW;
    SyncState dbState = myDBVersion.getSyncState();
    if (myDeleted) return dbState.afterLocalDelete();
    FlagVersion server = getServerVersion();
    if (server != null) {
      if (isEqualState(server, getStatus(), getRequestee(), false)) return SyncState.SYNC;
    }
    if (!isEdited()) return dbState;
    else return dbState.afterEdit();
  }

  public FlagStatus getServerStatus() {
    return myDBVersion != null ? myDBVersion.getServerStatus() : FlagStatus.UNKNOWN;
  }

  public boolean isCreated() {
    return myDBVersion == null;
  }

  public long getItem() {
    return myDBVersion != null ? myDBVersion.getItem() : 0;
  }

  public long getTypeItem() {
    return myType;
  }

  public FlagVersion getServerVersion() {
    return myDBVersion == null ? null : myDBVersion.getServerVersion();
  }

  public FlagVersion getOriginalVersion() {
    FlagVersion server = getServerVersion();
    return server != null ? server : myDBVersion;
  }

  @Nullable
  public static EditableFlag findByItem(long item, List<EditableFlag> source) {
    for (EditableFlag flag : source) if (flag.getItem() == item) return flag;
    return null;
  }
}
