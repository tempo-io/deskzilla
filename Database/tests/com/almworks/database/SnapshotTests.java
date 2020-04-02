package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.SquareCollectionModel;
import com.almworks.util.model.SquareCollectionModelEvent;
import util.concurrent.SynchronizedBoolean;
import util.concurrent.SynchronizedInt;

import java.util.Collection;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SnapshotTests extends WorkspaceFixture {
  public void testSnapshotContentKnown() throws InterruptedException {
    final Filter filter = myWorkspace.getFilterManager().attributeEquals(SystemObjects.ATTRIBUTE.TYPE,
      SystemObjects.TYPE.TYPE, true);
    final Snapshot snapshot = myWorkspace.getViews().getRootView().filter(filter).takeSnapshot();
    final SynchronizedBoolean notified = new SynchronizedBoolean(false);
    final SquareCollectionModel<Revision, ArtifactPointer, Value> values = snapshot.getArtifactValues();
    values.getEventSource().addStraightListener(new SquareCollectionModel.Adapter<Revision, ArtifactPointer, Value>() {
      public void onContentKnown(SquareCollectionModelEvent<Revision, ArtifactPointer, Value> event) {
        assertTrue(notified.commit(false, true));
      }
    });
    notified.waitForValue(true, 1000);
  }

  public void testSnapshotEventsOrder() throws InterruptedException {
    Artifact o1 = createObjectAndSetValue(myAttributeOne, "one");
    Artifact o2 = createObjectAndSetValue(myAttributeOne, "one");
    setObjectValue(o1, myAttributeTwo, "t1");
    setObjectValue(o2, myWorkspace.getSystemObject(SystemObjects.ATTRIBUTE.NAME), "t2");

    final Filter filter = myWorkspace.getFilterManager().attributeEquals(myAttributeOne, "one", true);
    final Snapshot snapshot = myWorkspace.getViews().getRootView()./*filter(filter).*/takeSnapshot();
    final SquareCollectionModel<Revision, ArtifactPointer, Value> values = snapshot.getArtifactValues();

    final SynchronizedBoolean full = new SynchronizedBoolean(false);
    final SynchronizedInt calls = new SynchronizedInt(0);

    values.getEventSource().addListener(ThreadGate.LONG(this), new SquareCollectionModel.Adapter<Revision, ArtifactPointer, Value>() {
      public void onRowsAdded(SquareCollectionModelEvent<Revision, ArtifactPointer, Value> event) {
        System.out.println("RowsAdded:" + event);
        assertFalse(full.get());
        calls.increment();
      }

      public void onColumnsAdded(SquareCollectionModelEvent<Revision, ArtifactPointer, Value> event) {
        System.out.println("ColumnsAdded:" + event);
        assertFalse(full.get());
        calls.increment();
      }

      public void onCellsSet(SquareCollectionModelEvent<Revision, ArtifactPointer, Value> event) {
        System.out.println("CellsSet:" + event);
        assertFalse(full.get());
        calls.increment();
      }

      public void onContentKnown(SquareCollectionModelEvent<Revision, ArtifactPointer, Value> event) {
        System.out.println("ContentKnown:" + event);
        assertTrue(full.commit(false, true));
        calls.increment();
      }
    });

    full.waitForValue(true, 1000);
    calls.waitForValueInRange(4, Integer.MAX_VALUE, 1000);
  }

  public void testGetAllArtifacts() {
    final Filter filter = myWorkspace.getFilterManager().attributeEquals(SystemObjects.ATTRIBUTE.TYPE,
      SystemObjects.TYPE.TYPE, true);
    Collection<Revision> artifacts = myWorkspace.getViews().getRootView().filter(filter).getAllArtifacts();
    assertTrue(artifacts.size() > 2);
  }

}
