package com.almworks.items.sync.edit;

import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.sync.util.*;
import com.almworks.items.util.AttributeMap;
import com.almworks.items.util.DBNamespace;
import com.almworks.util.commons.Procedure;
import com.almworks.util.tests.TimeGuard;
import junit.framework.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * Utility methods:<br><br>
 * <b>perform download of new or single item</b><br>
 * {@link #download(long, com.almworks.items.util.AttributeMap)}<br>
 * {@link #downloadNew(com.almworks.items.util.AttributeMap)} <br><br>
 * <b>edit single attribute of the existing item</b><br>
 * {@link #performEdit(long, com.almworks.items.api.DBAttribute, Object)}<br><br>
 * <b>check item state</b><br>
 * {@link #checkTrunk(long, com.almworks.items.api.DBAttribute, Object)}<br>
 * {@link #checkShadow(long, com.almworks.items.api.DBAttribute, com.almworks.items.api.DBAttribute, Object)}<br>
 * {@link #checkSyncState(long, com.almworks.items.sync.SyncState)}
 */
public abstract class SyncFixture extends MemoryDatabaseFixture {
  protected SyncManager myManager;
  protected TestEditFactory myEditFactory;
  protected ChangeNotificationLogger myNotifications;
  protected final TestSelector mySelector = new TestSelector();
  public static final DBNamespace TEST_NS = DBNamespace.moduleNs("test");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SyncManagerImpl syncManager = new SyncManagerImpl(db, mySelector);
    myManager = syncManager;
    myEditFactory = new TestEditFactory(myManager);
    myNotifications = ChangeNotificationLogger.listen(myManager.getModifiable());
    syncManager.runStartup();
  }

  protected SyncManagerImpl getManagerImpl() {
    return (SyncManagerImpl) myManager;
  }

  protected final void commitAndWait(EditCommit commit) throws InterruptedException {
    final CountDownLatch commitOk = new CountDownLatch(1);
    myManager.commitEdit(AggregatingEditCommit.toAggregating(commit).addProcedure(null, new EditCommit.Adapter() {
      @Override
      public void onCommitFinished(boolean success) {
        assertTrue(success);
        commitOk.countDown();
      }
    }));
    commitOk.await();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  protected DownloadValues createDownload(AttributeMap values) {
    return new DownloadValues(0, values);
  }

  protected DownloadValues createDownload(long item, AttributeMap values) {
    return new DownloadValues(item, values);
  }

  protected long download(long item, AttributeMap values) throws InterruptedException {
    return createDownload(item, values).perform(myManager);
  }

  protected long downloadNew(AttributeMap values) throws InterruptedException {
    return createDownload(values).perform(myManager);
  }

  protected <T> void performEdit(long item, DBAttribute<T> attribute, T value) throws InterruptedException {
    myEditFactory.edit(item).commit(attribute, value).waitReleased();
  }

  protected <T> T readAttribute(final long item, final DBAttribute<T> attribute)
    throws ExecutionException, InterruptedException
  {
    return db.readForeground(new ReadTransaction<T>() {
      @Override
      public T transaction(DBReader reader) throws DBOperationCancelledException {
        return reader.getValue(item, attribute);
      }
    }).get();
  }

  protected <T> void checkTrunk(long item, DBAttribute<T> attribute, T expected)
    throws ExecutionException, InterruptedException
  {
    assertEquals(expected, readAttribute(item, attribute));
  }

  protected <T> void checkShadow(long item, DBAttribute<AttributeMap> shadow, DBAttribute<T> attribute, T value)
    throws ExecutionException, InterruptedException
  {
    AttributeMap map = readAttribute(item, shadow);
    assertNotNull("Missing shadow " + shadow.toString(), map);
    assertEquals(value, map.get(attribute));
  }

  protected void checkSyncState(long item, SyncState state) throws ExecutionException, InterruptedException {
    assertEquals(state, getSyncState(item));
  }

  private SyncState getSyncState(final long item) throws ExecutionException, InterruptedException {
    return db.readBackground(new ReadTransaction<SyncState>() {
      @Override
      public SyncState transaction(DBReader reader) throws DBOperationCancelledException {
        ItemVersion trunk = SyncUtils.readTrunk(reader, item);
        ItemVersion base = SyncUtils.readBaseIfExists(reader, item);
        ItemVersion conflict = SyncUtils.readConflictIfExists(reader, item);
        SyncState state = trunk.getSyncState();
        if (base != null) assertEquals(state, base.getSyncState());
        if (conflict != null) assertEquals(state, conflict.getSyncState());
        return state;
      }
    }).get();
  }

  protected <T> void checkBase(long item, DBAttribute<T> attribute, T value) throws ExecutionException, InterruptedException {
    checkShadow(item, SyncSchema.BASE, attribute, value);
  }

  protected <T> void checkConflict(long item, DBAttribute<T> attribute, T value) throws ExecutionException, InterruptedException {
    checkShadow(item, SyncSchema.CONFLICT, attribute, value);
  }

  protected void waitForCanUpload(final long item) throws InterruptedException {
    TimeGuard.waitFor(new Procedure<TimeGuard<Object>>() {
      @Override
      public void invoke(TimeGuard<Object> arg) {
        if (myManager.canUpload(item)) arg.setResult(null);
      }
    });
  }

  protected long[] getSlaves(final long item, final DBAttribute<Long> master) throws ExecutionException, InterruptedException {
    return db.readForeground(new ReadTransaction<long[]>() {
      @Override
      public long[] transaction(DBReader reader) throws DBOperationCancelledException {
        return SyncUtils.readTrunk(reader, item).getSlaves(master).toNativeArray();
      }
    }).get();
  }

  public static class DownloadValues implements DownloadProcedure<DBDrain> {
    private final long myItem;
    private final AttributeMap myValues;
    private final TestReference<Long> myDoneItem = new TestReference<Long>();

    public DownloadValues(long item, AttributeMap values) {
      myItem = item;
      myValues = values;
    }

    @Override
    public void onFinished(DBResult<?> result) {
      boolean successful = result.isSuccessful();
      Assert.assertTrue(successful);
      if (!successful) assertTrue(myDoneItem.deferValue(null));
      else assertNotNull(myDoneItem.getDeferredValue());
      myDoneItem.publishValue();
    }

    @Override
    public void write(DBDrain drain) throws DBOperationCancelledException {
      ItemVersionCreator creator = myItem > 0 ? drain.changeItem(myItem) : drain.createItem();
      creator.setAlive();
      SyncUtils.copyValues(creator, myValues);
      assertNull(myDoneItem.getDeferredValue());
      myDoneItem.deferValue(creator.getItem());
    }

    public long perform(SyncManager manager) throws InterruptedException {
      manager.writeDownloaded(this);
      return waitDone();
    }

    public long waitDone() throws InterruptedException {
      return myDoneItem.waitForPublished();
    }
  }
}
