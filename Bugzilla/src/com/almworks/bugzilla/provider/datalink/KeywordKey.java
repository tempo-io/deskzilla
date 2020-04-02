package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.application.BadItemException;
import com.almworks.api.application.ResolvedItem;
import com.almworks.items.api.DBReader;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.commons.Condition;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

/**
 * @author Alex
 */

public class KeywordKey extends ResolvedItem {
  private final String myDescription;
  private final Boolean mySynced;

  public static final Condition<KeywordKey> SYNCHRONIZED = new Condition<KeywordKey>() {
    @Override
    public boolean isAccepted(KeywordKey value) {
      assert value != null;
      return value != null && value.getSyncronized() == Boolean.TRUE;
    }
  };
/*
  public static final Comparator<KeywordKey> SYNCED_COMPARATOR = new Comparator<KeywordKey>() {
    public int compare(KeywordKey o1, KeywordKey o2) {
      int i1 = o1.mySynced == null ? 1 : (o1.mySynced ? 2 : 0);
      int i2 = o2.mySynced == null ? 1 : (o2.mySynced ? 2 : 0);
      return i2 - i1;
    }
  };
*/

  private KeywordKey(long item, long connectionItem, @NotNull String id, @NotNull String description, String displayable, Boolean synced) {
    super(item, displayable, null, id, null, connectionItem);
    myDescription = description;
    mySynced = synced;
  }

  public String getDescription() {
    return myDescription;
  }

  public boolean isSame(ResolvedItem that) {
    if (!super.isSame(that))
      return false;

    KeywordKey thatKey = (KeywordKey) that;

    if (!Util.equals(thatKey.mySynced, mySynced))
      return false;
    if (!Util.equals(thatKey.myDescription, myDescription))
      return false;

    return true;
  }

  public Boolean getSyncronized() {
    return mySynced;
  }

  public static KeywordKey keyword(long item, DBReader reader) throws BadItemException {
    String id = reader.getValue(item, KeywordLink.attrId);
    if (id == null || id.isEmpty()) throw new BadItemException("no keyword", item);
    String description = Util.NN(reader.getValue(item, KeywordLink.attrDescription));
    String displayName = Util.NN(reader.getValue(item, KeywordLink.attrDisplay), id);
    Boolean synced = reader.getValue(item, KeywordLink.attrSynced);
    long connectionItem = Util.NN(reader.getValue(item, SyncAttributes.CONNECTION), 0L);
    return new KeywordKey(item, connectionItem, id, description, displayName, synced);
  }
}
