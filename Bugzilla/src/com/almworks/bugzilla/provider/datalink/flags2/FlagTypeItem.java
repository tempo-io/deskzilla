package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.ResolvedItem;
import com.almworks.integers.LongList;
import com.almworks.util.collections.LongSet;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public class FlagTypeItem extends ResolvedItem {
  private final UIFlagData myData;
  private final int myId;
  private final int myFlags;
  private final String myDescription;
  private final LongSet myPlusComponents;
  private final LongSet myMinusComponents;

  private FlagTypeItem(long item, @NotNull String name, long connectionItem, UIFlagData data, int flags, int id,
    String description, LongList applicablePlus, LongList applicableMinus)
  {
    super(item, name, null, name, null, connectionItem);
    myData = data;
    myFlags = flags;
    myId = id;
    myDescription = description;
    myPlusComponents = LongSet.copy(applicablePlus);
    myMinusComponents = LongSet.copy(applicableMinus);
  }

  public static FlagTypeItem create(LoadedFlagType loaded, UIFlagData uidata) {
    return new FlagTypeItem(loaded.getItem(), loaded.getName(), loaded.getConnection(), uidata, loaded.getOptionFlags(), loaded.getTypeId(),
      loaded.getDescription(), loaded.getApplicability(true), loaded.getApplicability(false));
  }

  public int getTypeId() {
    return myId;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean allowsAnyStatus() {
    for (char status : LoadedFlagType.ALL_STATUSES) if (allowsStatus(FlagStatus.fromChar(status))) return true;
    return false;
  }

  public boolean isSureSingleton() {
    return LoadedFlagType.isSureSingleton(myFlags);
  }

  public boolean allowsStatus(FlagStatus status) {
    return LoadedFlagType.allowsStatus(myFlags, status);
  }

  public boolean isSureSpecificallyRequestable(boolean sureValue) {
    return LoadedFlagType.isSureSpecificallyRequestable(myFlags, sureValue);
  }

  public UIFlagData getData() {
    return myData;
  }

  public boolean intersectsComponents(LongSet components, boolean plus) {
    LongSet own = plus ? myPlusComponents : myMinusComponents;
    return own.intersects(components);
  }

  public boolean containsComponents(LongSet components, boolean plus) {
    LongSet ownApplicability = plus ? myPlusComponents : myMinusComponents;
    return ownApplicability.containsAll(components);
  }

  @Override
  public boolean isSame(ResolvedItem that) {
    if (!super.isSame(that))
      return false;
    FlagTypeItem thatType = (FlagTypeItem) that;
    if (
      !Util.equals(thatType.myId, myId) ||
      !Util.equals(thatType.myFlags, myFlags) ||
      !Util.equals(thatType.myPlusComponents, myPlusComponents) ||
      !Util.equals(thatType.myMinusComponents, myMinusComponents) ||
      !Util.equals(thatType.myDescription, myDescription)
      ) return false;
    return true;
  }
}
