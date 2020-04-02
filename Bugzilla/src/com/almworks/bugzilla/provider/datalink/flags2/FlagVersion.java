package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.BadItemException;
import com.almworks.api.application.ItemKey;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.SyncState;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Convertors;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.List;

public class FlagVersion implements Flag {

  public final static Convertor<Flag, String> TO_COMPRESSED_PRESENTATION = new Convertor<Flag, String>() {
    @Override
    public String convert(Flag flag) {
      return flag.getName() + flag.getStatus().getDisplayPresentation();
    }
  };

  public final static Convertor<Flag, String> TO_BUGZILLA_PRESENTATION = new Convertor<Flag, String>() {
    @Override
    public String convert(Flag flag) {
      String setterName = flag.getSetterName();
      String requesteeName = flag.getRequesteeName();
      final String setter = setterName.length() > 0 ? (setterName + ": ") : "";
      final String requestee = requesteeName.length() > 0 ? " (" + requesteeName + ')' : "";
      return setter + flag.getName() + flag.getStatus().getDisplayPresentation() + requestee;
    }
  };

  public static final Convertor<FlagVersion, String> TO_MATCH_PATTERN_STRING = new Convertor<FlagVersion, String>() {
    @Override
    public String convert(FlagVersion flag) {
      return flag.getName() + flag.getStatusChar();
    }
  };

  public final static Convertor<Flag, String> TO_BUGZILLA_PRESENTATION_HYPHEN = TO_BUGZILLA_PRESENTATION.composition(
    Convertors.replaceAll("\u2212", "-"));


  private final PrivateMetadata myPm;
  private final long myType;
  private final FlagStatus myStatus;
  private final long mySetter;
  private final long myRequestee;
  private final SyncState mySyncState;
  @Nullable
  private final FlagVersion myServerVersion;
  private final long myItem;
  private final boolean myDeleted;

  private FlagVersion(PrivateMetadata pm, long flag, long setter, long type, long requestee, SyncState state, char status, FlagVersion serverVersion, boolean deleted) {
    myPm = pm;
    myType = type;
    myStatus = FlagStatus.fromChar(status);
    mySetter = setter;
    myRequestee = requestee;
    mySyncState = state;
    myServerVersion = serverVersion;
    myItem = flag;
    myDeleted = deleted;
  }


  public static String getSummaryString(List<? extends Flag> flags) {
    if(flags == null || flags.isEmpty()) return "";
    final Convertor<Flag, String> presentation = flags.size() == 1 ? TO_BUGZILLA_PRESENTATION : TO_COMPRESSED_PRESENTATION;
    return TextUtil.separate(flags, " ", presentation);
  }

  public static List<FlagVersion> load(ItemVersion bug, @NotNull PrivateMetadata pm) {
    List<FlagVersion> result = Collections15.arrayList();
    LongList flags = bug.getSlaves(Flags.AT_FLAG_MASTER);
    for (ItemVersion flag : bug.readItems(flags)) {
      FlagVersion uiFlag = loadFlag(pm, flag, true);
      result.add(uiFlag);
    }
    return result;
  }

  private static FlagVersion loadFlag(PrivateMetadata pm, ItemVersion flag, boolean loadServer) {
    SyncState syncState = flag.getSyncState();
    long setter = flag.getNNValue(Flags.AT_FLAG_SETTER, 0L);
    long type = flag.getNNValue(Flags.AT_FLAG_TYPE, 0L);
    long requestee = flag.getNNValue(Flags.AT_FLAG_REQUESTEE, 0L);
    char status = flag.getNNValue(Flags.AT_FLAG_STATUS, 'X');
    boolean deleted = flag.isInvisible();
    FlagVersion serverFlag = null;
    if (loadServer) {
      ItemVersion server = flag.switchToServer();
      if (server != null) serverFlag = loadFlag(pm, server, false);
    }
    FlagVersion uiFlag = new FlagVersion(pm, flag.getItem(), setter, type, requestee, syncState, status, serverFlag, deleted);
    UIFlagData data = uiFlag.getData();
    DBReader reader = flag.getReader();
    try {
      data.ensureTypeKnown(reader, type);
      data.ensureUserKnown(reader, setter);
      data.ensureUserKnown(reader, requestee);
    } catch (BadItemException e) {
      Log.error(e);
    }
    return uiFlag;
  }

  public String getName() {
    return getData().getTypeName(myType);
  }

  public char getStatusChar() {
    return myStatus.getChar();
  }

  public SyncState getSyncState() {
    return mySyncState;
  }

  public FlagStatus getStatus() {
    return myStatus;
  }

  public ItemKey getRequesteeKey() {
    return getData().getUserKey(myRequestee);
  }

  public long getRequesteeItem() {
    return myRequestee;
  }

  public boolean isEqualState(FlagVersion f2) {
    return isEqualState(f2.getStatusChar(), f2.getRequesteeItem(), f2.isDeleted());
  }

  public boolean isEqualState(char status, long requestee, boolean deleted) {
    if (deleted != isDeleted()) return false;
    if (status != '?') requestee = 0;
    long ownRequestee = getRequesteeItem();
    char ownStatus = getStatusChar();
    if (ownStatus != '?') ownRequestee = 0;
    return ownStatus == status && (ownRequestee == requestee || (ownRequestee <= 0 && requestee <= 0));
  }

  public ItemKey getSetterKey() {
    return getData().getUserKey(mySetter);
  }

  public String getRequesteeName() {
    return getData().getUserName(myRequestee);
  }

  public String getSetterName() {
    return getData().getUserName(mySetter);
  }

  public String getDescription() {
    return getData().getTypeDescription(myType);
  }

  public UIFlagData getData() {
    return myPm.getCommonMetadata().myFlags;
  }

  public long getTypeItem() {
    return myType;
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public boolean isThisUserRequested() {
    return myRequestee > 0 && myRequestee == myPm.getThisUser();
  }

  public FlagStatus getServerStatus() {
    if (myServerVersion != null) return myServerVersion.getStatus();
    if (getSyncState().isLocalOnly()) return FlagStatus.UNKNOWN;
    return getStatus();
  }

  public long getItem() {
    return myItem;
  }

  @Nullable
  public FlagVersion getServerVersion() {
    return myServerVersion;
  }
}
