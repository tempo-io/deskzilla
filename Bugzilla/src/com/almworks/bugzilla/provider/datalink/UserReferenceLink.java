package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.engine.EngineUtils;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import org.almworks.util.Util;

public class UserReferenceLink extends SingleReferenceLink<String> {
  private final boolean myPrototypeCurrentUser;
  private final boolean myReadonly;

  public UserReferenceLink(DBAttribute<Long> attribute, BugzillaAttribute bugzillaAttribute,
    boolean ignoreEmpty, boolean prototypeCurrentUser, boolean readonly)
  {
    super(attribute, bugzillaAttribute, User.type, User.EMAIL, User.DISPLAY_NAME, ignoreEmpty, null, true);
    myPrototypeCurrentUser = prototypeCurrentUser;
    myReadonly = readonly;
  }

  @Override
  public void autoMerge(AutoMergeData data) {
    // done
    if (myReadonly) {
      data.discardEdit(getAttribute());
    } else {
      super.autoMerge(data);
    }
  }

  public Long getPrototypeValue(PrivateMetadata pm, DBReader reader) {
    return myPrototypeCurrentUser ? EngineUtils.getConnectionUser(pm.thisConnection, reader) : null;
  }

  public static String getUniqueUserKey(String keyValue) {
    return keyValue == null ? null : Util.lower(keyValue).trim();
  }

  public boolean areKeyValuesEqual(String newValue, String currentValue) {
    if (newValue == null)
      return currentValue == null;
    return newValue.equalsIgnoreCase(currentValue);
  }

  @Override
  public ItemProxy createProxy(final PrivateMetadata pm, String keyValue) {
    String email = User.normalizeEmailId(keyValue);
    final BugzillaUser bzUser = BugzillaUser.longEmailName(email, null);
    final ItemProxy general = super.createProxy(pm, email);
    return new ItemProxy() {
      @Override
      public long findOrCreate(DBDrain drain) {
        return User.getOrCreate(drain, bzUser, pm);
      }

      @Override
      public long findItem(DBReader reader) {
        return general.findItem(reader);
      }
    };
  }

  public String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
    String string = super.getRemoteString(bugInfo, privateMetadata);
    string = User.normalizeEmailId(string);
    return string;
  }

  public String detectFailedUpdateString(
    PrivateMetadata privateMetadata, BugzillaAttribute attribute, String requested, String received)
  {
    // the server is free to suggest any value for user fields
    return null;
  }

  @Override
  public String createRemoteString(Long item, ItemVersion lastServerVersion) {
    if(item == null || item <= 0L) {
      return null;
    }
    return lastServerVersion.forItem(item).getValue(getReferentUniqueKey());
  }

  @Override
  public String getLocalString(ItemVersion referent, BugzillaConnection connection) throws BadReferent {
    return User.stripSuffix(super.getLocalString(referent, connection), connection);
  }
}
