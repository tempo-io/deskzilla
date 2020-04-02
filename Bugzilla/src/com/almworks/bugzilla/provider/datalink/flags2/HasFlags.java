package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Env;
import com.almworks.util.advmodel.*;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ValueModel;
import org.almworks.util.Util;

public class HasFlags {
  private static final String DESKZILLA_FORCE_FLAGS = "deskzilla.force.flags";
  private final SegmentedListModel<ConstraintDescriptor> myDescriptorsModel = SegmentedListModel.create(AListModel.EMPTY);
  private final BugzillaConnection myConnection;
  private final ValueModel<Boolean> myConnectionHasFlags = ValueModel.create();
  private final UpdateHasFlags myListener = new UpdateHasFlags();

  public HasFlags(BugzillaConnection connection) {
    myConnection = connection;
    myConnectionHasFlags.addAWTChangeListener(myListener);
    attachFlagTypeListeners();
  }

  private void attachFlagTypeListeners() {
    final BoolExpr<DP> isFlagType = DPEqualsIdentified.create(SyncAttributes.CONNECTION, myConnection.getConnectionRef())
      .and(DPEqualsIdentified.create(DBAttribute.TYPE, Flags.KIND_TYPE));
    getDb().liveQuery(myConnection.getContext().getConnectionLife(), isFlagType, new DBLiveQuery.Listener() {
      @Override
      public void onICNPassed(long icn) {
      }

      @Override
      public void onDatabaseChanged(DBEvent event, DBReader reader) {
        myConnectionHasFlags.setValue(reader.query(isFlagType).count() > 0);
      }
    });
  }

  public void start() {
    getDb().readBackground(new ReadTransaction<Object>() {
      @Override
      public Object transaction(DBReader reader) throws DBOperationCancelledException {
        long connection = reader.findMaterialized(myConnection.getConnectionRef());
        if (connection <= 0) return null;
        myConnectionHasFlags.setValue(reader.getValue(connection, Flags.AT_CONNECTION_HAS_FLAGS));
        return null;
      }
    });
    if (isForceFlags())
      ThreadGate.AWT.execute(new Runnable() {
        @Override
        public void run() {
          myListener.onChange();
        }
      });
  }

  private Database getDb() {
    return myConnection.getCommonMD().getActor(Database.ROLE);
  }

  public AListModel<ConstraintDescriptor> getDescriptorsModel() {
    return myDescriptorsModel;
  }

  public boolean mayHasFlags() {
    return mayHasFlags(myConnectionHasFlags.getValue());
  }

  private static boolean isForceFlags() {
    return Env.getBoolean(DESKZILLA_FORCE_FLAGS, false);
  }

  private static boolean mayHasFlags(Boolean value) {
    return value == null || value || isForceFlags();
  }

  public void update(final boolean hasBugFlags) {
    myConnectionHasFlags.setValue(hasBugFlags);
  }

  private class UpdateHasFlags implements ChangeListener {
    private Boolean myLastHasFlags = false;
    private AListModel<ConstraintDescriptor> myFlagDescriptors = null;

    @Override
    public void onChange() {
      Boolean hasFlags = myConnectionHasFlags.getValue();
      if (Util.equals(myLastHasFlags, hasFlags)) return;
      myLastHasFlags = hasFlags;
      boolean mayHave = mayHasFlags(hasFlags);
      final AListModel<ConstraintDescriptor> flags = getFlagDescriptors();
      //noinspection unchecked
      myDescriptorsModel.setSegment(0, mayHave ? flags : AListModel.EMPTY);
      myConnection.getCommonMD().getActor(SyncManager.ROLE).writeDownloaded(new DownloadProcedure<DBDrain>() {
        @Override
        public void write(DBDrain drain) throws DBOperationCancelledException {
          ItemVersionCreator connection = drain.changeItem(myConnection.getConnectionRef());
          connection.setValue(Flags.AT_CONNECTION_HAS_FLAGS, myConnectionHasFlags.getValue());
        }

        @Override
        public void onFinished(DBResult<?> result) {}
      });
    }

    private AListModel<ConstraintDescriptor> getFlagDescriptors() {
      if (myFlagDescriptors == null) {
        CommonMetadata md = myConnection.getCommonMD();
        myFlagDescriptors = OrderListModel.create(md.myFlags.createConstraintDecriptors(md));
      }
      return myFlagDescriptors;
    }
  }
}
