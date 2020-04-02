package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumGrouping;
import com.almworks.api.explorer.gui.*;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.ResolvedUser;
import com.almworks.bugzilla.provider.datalink.UserReferenceLink;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongIterator;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Env;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.ui.ColorUtil;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;

public class User {
  public static final String SHOW_EMAIL_INSTEAD_OF_LOGIN_PROP = "dz.show.user.email";
  private static final Boolean SHOW_EMAIL_INSTEAD_OF_LOGIN = Env.getBoolean(SHOW_EMAIL_INSTEAD_OF_LOGIN_PROP, false);

  public static final DBNamespace NS = BugzillaProvider.NS.subNs("user");
  public static final List<EnumGrouping> GROUPING = Collections15.<EnumGrouping>unmodifiableListCopy(new UserKeyGrouping());
  public static final DBItemType type = NS.type();
  public static final DBAttribute<String> DISPLAY_NAME = NS.string("displayName", "Display Name", false);
  public static final DBAttribute<String> EMAIL = NS.string("email", "E-Mail", false);
  public static final DBAttribute<Boolean> DISABLED = NS.bool("disabled", "Disabled?", false);

  private static final Condition<String> VALID_EMAIL = new Condition<String>() {
    public boolean isAccepted(String s) {
      return s != null && s.trim().length() > 0;
    }
  };

  private static final Convertor<String, String> UNIQUE_USER_KEY_CONVERTOR = new Convertor<String, String>() {
    public String convert(String s) {
      return UserReferenceLink.getUniqueUserKey(s);
    }
  };

  public static final TextResolver userResolver =
    new KeyAttributeResolver(EMAIL, type, new UserItemFactory(), VALID_EMAIL, UNIQUE_USER_KEY_CONVERTOR);

  public static ResolvedFactory<ResolvedItem> KEY_FACTORY = new ResolvedFactory<ResolvedItem>() {
    @Override
    public ResolvedItem createResolvedItem(long item, DBReader reader) {
      ItemVersion trunk = SyncUtils.readTrunk(reader, item);
      String login = trunk.getValue(DISPLAY_NAME);
      String email = trunk.getValue(EMAIL);
      String displayName = SHOW_EMAIL_INSTEAD_OF_LOGIN ? email : login;
      if (displayName == null)
        displayName = email;
      ResolvedItem ri = displayName != null ? ResolvedItem.create(trunk, displayName, email) : null;
      if (ri == null) {
        assert false : item;
        Log.debug("null key for " + item);
        return null;
      }
      Boolean enabled = !Boolean.TRUE.equals(reader.getValue(item, DISABLED));
      return new ResolvedUser(ri, enabled);
    }
  };

  public static final EnumType ENUM_USERS = new EnumType(type, DISPLAY_NAME, EMAIL, GROUPING, ItemKey.GET_ID,  "users", null, userResolver, KEY_FACTORY);

  public static final CanvasRenderer<ItemKey> RENDERER = createRenderer(null);

  @Nullable
  public static String getRemoteId(@Nullable ItemVersion user) {
    return user != null ? user.getValue(EMAIL) : null;
  }

  public static boolean equalId(ItemVersion user, String id) {
    String dbId = getRemoteId(user);
    return Util.equals(dbId, id);
  }

  public static boolean equalId(ItemVersion user, BugzillaUser bugzillaUser) {
    String dbId = getRemoteId(user);
    return bugzillaUser.equalId(dbId);
  }

  public static String normalizeEmailId(String keyValue) {
    keyValue = Util.lower(keyValue);
    if (keyValue != null) keyValue = keyValue.trim();
    return keyValue;
  }

  private static class UserItemFactory implements ResolverItemFactory {
    public long createItem(String text, UserChanges changes) {
      assert text != null && text.trim().length() > 0 : changes;
      BugzillaConnection connection = Util.castNullable(BugzillaConnection.class, changes.getConnection());
      if (connection == null) {
        assert false;
        return 0;
      }
      PrivateMetadata pm = connection.getContext().getPrivateMetadata();
      return getOrCreateFromUserInput(changes.getCreator(), text, pm);
    }
  }

  public static long getOrCreate(DBDrain drain, BugzillaUser user, PrivateMetadata pm) {
    final ItemVersionCreator creator = getOrCreateInternal(drain, user, pm);
    return creator != null ? creator.getItem() : -1L;
  }
  
  public static long getOrCreateFromUserInput(DBDrain drain, String userEnteredText, PrivateMetadata pm) {
    return getOrCreate(drain, BugzillaUser.shortEmailName(userEnteredText, null, pm.getEmailSuffix()), pm);
  }

  public static void setEnabledUsers(DBDrain drain, Collection<BugzillaUser> users, PrivateMetadata pm) {
    if(users.isEmpty()) {
      markAllUsersEnabled(drain, pm);
      return;
    }

    final LongSet enabled = new LongSet();
    for(final BugzillaUser user : users) {
      final ItemVersionCreator creator = getOrCreateInternal(drain, user, pm);
      if(creator != null) {
        creator.setValue(DISABLED, null);
        enabled.add(creator.getItem());
      }
    }

    final LongArray items = queryAllUsers(drain, pm).copyItemsSorted();
    items.removeAll(enabled);
    for(final LongIterator it = items.iterator(); it.hasNext();) {
      drain.changeItem(it.next()).setValue(DISABLED, true);
    }
  }

  private static void markAllUsersEnabled(DBDrain drain, PrivateMetadata pm) {
    final LongArray items = queryAllUsers(drain, pm).copyItemsSorted();
    for(final LongIterator it = items.iterator(); it.hasNext();) {
      drain.changeItem(it.next()).setValue(DISABLED, null);
    }
  }

  private static ItemVersionCreator getOrCreateInternal(DBDrain drain, BugzillaUser user, PrivateMetadata pm) {
    if (user == null) {
      return null;
    }

    final String lowercaseEmail = user.getEmailId();
    final long existing = queryAllUsers(drain, pm).getItemByKey(EMAIL, lowercaseEmail);
    if (existing <= 0) {
      ItemVersionCreator newItem = drain.createItem();
      newItem.setValue(SyncAttributes.CONNECTION, pm.thisConnection);
      newItem.setValue(DBAttribute.TYPE, type);
      newItem.setValue(EMAIL, lowercaseEmail);
      newItem.setValue(DISPLAY_NAME, user.getDisplayName());
      return newItem;
    }

    final ItemVersionCreator existingCreator = drain.changeItem(existing);
    if (user.isDisplayNameKnown()) {
      existingCreator.setValue(DISPLAY_NAME, user.getDisplayName());
    }
    existingCreator.setAlive();
    return existingCreator;
  }

  private static DBQuery queryAllUsers(DBDrain drain, PrivateMetadata pm) {
    final BoolExpr<DP> expr = BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, type),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, pm.thisConnection));
    return drain.getReader().query(expr);
  }

  public static void updateSuffix(DBDrain drain, String suffix, DBIdentifiedObject connection) {
    Log.debug("applying email suffix setting: " + suffix);
    final BoolExpr<DP> allUsersQuery = BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, type),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, connection));

    final LongArray allUsers = drain.getReader().query(allUsersQuery).copyItemsSorted();
    int count = 0;
    for(int i = 0; i < allUsers.size(); i++) {
      final ItemVersionCreator user = drain.changeItem(allUsers.get(i));
      if(fixAttribute(user, EMAIL, suffix)) {
        count++;
      }
    }

    Log.debug("changed " + count + " user items according to the email suffix setting");
  }

  private static boolean fixAttribute(ItemVersionCreator user, DBAttribute<String> attr, String suffix) {
    final String original = user.getValue(attr);
    if(original != null) {
      final String trimmed = original.trim();
      if(trimmed.endsWith(suffix)) {
        final String fixed = trimmed.substring(0, trimmed.length() - suffix.length());
        user.setValue(attr, fixed);
        return true;
      }
    }
    return false;
  }

  public static CanvasRenderer<ItemKey> createRenderer(@Nullable final String none) {
    return new CanvasRenderer<ItemKey>() {
      @Override
      public void renderStateOn(CellState state, Canvas canvas, ItemKey item) {
        if (item == ItemKey.INVALID) {
          canvas.appendText(none != null ? none : "<None>");
          canvas.setFontStyle(Font.ITALIC);
        } else {
          String displayName = item.getDisplayName();
          String id = item.getId();
          canvas.appendText(displayName);
          if (!Util.equals(id, displayName)) {
            CanvasSection section = canvas.newSection();
            section.setForeground(ColorUtil.between(state.getOpaqueBackground(), state.getForeground(), 0.5f));
            section.appendText(" " + id);
          }
        }
      }
    };
  }

  public static String stripSuffix(String email, BugzillaConnection conn) {
    if(email != null && !email.isEmpty() && conn != null) {
      final ScalarModel<OurConfiguration> model = conn.getContext().getConfiguration();
      if(model.isContentKnown()) {
        final OurConfiguration config = model.getValue();
        if(config != null) {
          final String suffix = config.getEmailSuffixIfUsing();
          if(suffix != null && !suffix.isEmpty() && email.endsWith(suffix)) {
            return email.substring(0, email.length() - suffix.length());
          }
        }
      }
    }
    return email;
  }

  private static class UserKeyGrouping implements EnumGrouping<ItemKeyGroup>, Comparator<ItemKeyGroup> {
    public ItemKeyGroup getGroup(ResolvedItem item) {
      String name = item.getId();
      int dog = name.indexOf('@');
      if (dog < 0 || dog == name.length() - 1)
        return null;
      else
        return new ItemKeyGroup(name.substring(dog + 1));
    }

    @NotNull
    public Comparator<ItemKeyGroup> getComparator() {
      return this;
    }

    @NotNull
    public String getDisplayableName() {
      return "domain";
    }

    public int compare(ItemKeyGroup o1, ItemKeyGroup o2) {
      return String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayableName(), o2.getDisplayableName());
    }
  }
}
