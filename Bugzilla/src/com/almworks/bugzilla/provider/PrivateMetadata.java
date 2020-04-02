package com.almworks.bugzilla.provider;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.properties.Role;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class PrivateMetadata {
  public static final Role<PrivateMetadata> ROLE = Role.role("privateMetadata");

  public final DBIdentifiedObject thisConnection;
  private volatile long thisConnectionItem = 0;
  public final DBIdentifiedObject bugPrototype;
  private volatile long bugPrototypeItem = 0;

  private volatile long myCachedUser = 0;

  private final DBNamespace myNamespace;
  private final Connection myConnection;
  private final CommonMetadata myMd;

  private volatile OurConfiguration myLastConfig = null;

  private final DBFilter myBugsView;

  public PrivateMetadata(Connection connection, CommonMetadata md) {
    myConnection = connection;
    myMd = md;
    String connId = connection.getConnectionID();
    myNamespace = BugzillaProvider.NS.subNs(connId);

    thisConnection = myNamespace.object("thisConnection", "Bugzilla");
    thisConnection.initialize(DBAttribute.TYPE, CommonMetadata.typeConnection);
    thisConnection.initialize(CommonMetadata.attrConnectionID, connId);

    bugPrototype = myNamespace.object("bugPrototype", "Bug Prototype");
    bugPrototype.initialize(DBAttribute.TYPE, Bug.typeBug);
    bugPrototype.initialize(SyncAttributes.CONNECTION, thisConnection);
    bugPrototype.initialize(SyncAttributes.IS_PROTOTYPE, true);

    Database db = getActor(Database.ROLE);
    assert db != null;
    myBugsView = db.filter(BoolExpr.and(DPEqualsIdentified.create(SyncAttributes.CONNECTION, thisConnection), Bug.IS_BUG));

  }

  public final ComponentContainer getContainer() {
    return myConnection.getConnectionContainer();
  }

  public long getThisUser() {
    assert !Database.require().isDbThread() : "use database";
    return myCachedUser;
  }

  public final <T> T getActor(Role<T> role) {
    return getContainer().getActor(role);
  }

  public void updateConfiguration(final OurConfiguration oldConfig, final OurConfiguration newConfig) {
    if(newConfig == null) {
      return;
    }

    myLastConfig = newConfig;
    getActor(SyncManager.ROLE).writeDownloaded(new DownloadProcedure<DBDrain>() {
      private long thisUser = -1L;

      @Override
      public void write(DBDrain drain) throws DBOperationCancelledException {
        updateUser(drain, newConfig);
        updateBugPrototype(drain);
        updateUserAttributes(drain, oldConfig, newConfig);
      }

      @Override
      public void onFinished(DBResult<?> result) {
        if (result.isSuccessful() && thisUser != -1L) myCachedUser = thisUser;
      }

      private void updateUser(DBDrain drain, OurConfiguration configuration) {
        long result = 0;
        if (!configuration.isAnonymousAccess()) {
          final String userEmail = Util.NN(configuration.getUsername()).trim();
          assert userEmail.length() > 0 : configuration;
          if (userEmail.length() != 0) {
            result = User.getOrCreate(drain, BugzillaUser.shortEmailName(userEmail, null, configuration.getEmailSuffixIfUsing()), PrivateMetadata.this);
          }
        }
        drain.changeItem(thisConnection).setValue(Connection.USER, result);
        thisUser = result;
      }
    });
  }


  public void updateBugPrototype(DBDrain drain) {
    Bug.updatePrototype(drain.changeItem(bugPrototype), this);
  }

  private void updateUserAttributes(DBDrain writer, OurConfiguration oldConfig, OurConfiguration newConfig) {
    if (oldConfig == null)
      return;
    boolean wasUsing = oldConfig.isUsingEmailSuffix();
    boolean isUsing = newConfig.isUsingEmailSuffix();
    if (!wasUsing && isUsing) {
      final String suffix = Util.lower(Util.NN(newConfig.getEmailSuffix()));
      assert suffix != null && !suffix.isEmpty() : newConfig;
      if(!suffix.isEmpty()) {
        User.updateSuffix(writer, suffix, thisConnection);
      }
    }
  }

  public String getEmailSuffix() {
    OurConfiguration lastConfig = myLastConfig;
    if (lastConfig == null)
      return null;
    if (!lastConfig.isUsingEmailSuffixSetting())
      return null;
    String suffix = lastConfig.getEmailSuffix();
    if (suffix == null || suffix.length() == 0)
      return null;
    return suffix;
  }

  @Nullable
  public String getThisUserId() {
    OurConfiguration config = myLastConfig;
    if (config == null || config.isAnonymousAccess()) return null;
    return Util.NN(config.getUsername()).trim();
  }

  public void processAccountNameSuggestedByBugzilla(final BugzillaUser accountName) {
    if (accountName == null) {
      // no user?
      return;
    }
    getActor(SyncManager.ROLE).writeDownloaded(new DownloadProcedure<DBDrain>() {
      private long thisUser = -1L;

      @Override
      public void write(DBDrain drain) throws DBOperationCancelledException {
        ItemVersion user = drain.forItem(drain.forItem(thisConnection).getNNValue(Connection.USER, 0L));
        String currentName = user.getValue(User.EMAIL);
        if (currentName == null || accountName.equalId(currentName)) {
          return;
        }
        // todo check validity of changing user object
        // the following code would create another object :
        thisUser = User.getOrCreate(drain, accountName, PrivateMetadata.this);
        drain.changeItem(thisConnection).setValue(Connection.USER, thisUser);
      }

      @Override
      public void onFinished(DBResult<?> result) {
        if (result.isSuccessful() && thisUser != -1L) myCachedUser = thisUser;
      }
    });
  }

  public CommonMetadata getCommonMetadata() {
    return myMd;
  }

  public void materialize(DBWriter writer) {
    thisConnectionItem = writer.materialize(thisConnection);
    bugPrototypeItem = writer.materialize(bugPrototype);
  }

  public long thisConnectionItem() {
    final long item = thisConnectionItem;
    assert item > 0;
    return item;
  }

  public long bugPrototypeItem() {
    final long item = bugPrototypeItem;
    assert item > 0;
    return item;
  }

  public DBNamespace namespace() {
    return myNamespace;
  }

  public DBFilter getBugsView() {
    return myBugsView;
  }

  public DBIdentifiedObject getConnectionRef() {
    return thisConnection;
  }
}
