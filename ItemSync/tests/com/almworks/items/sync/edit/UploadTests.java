package com.almworks.items.sync.edit;

import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.tests.CollectionsCompare;

import java.util.concurrent.ExecutionException;

public class UploadTests extends SingleAttributeFixture {
  private static final CollectionsCompare CHECK = new CollectionsCompare();
  public static final DBAttribute<Long> MASTER = DBAttribute.Link("test.master", "Master", true);

  public void testConflict() throws ExecutionException, InterruptedException {
    long item1 = downloadItem(1, "abc");
    editItem(item1, "local", "abc");
    checkWholeTrunk(item1, SyncState.EDITED, 1, "local");

    downloadUpdated(item1, "server");
    checkWholeTrunk(item1, SyncState.CONFLICT, 1, "local");
    checkConflict(item1, TEXT, "server");

    long item2 = downloadItem(2, "a");
    editItem(item2, "b", "a");

    TestUploader uploader = TestUploader.beginUpload(myManager, item1);
    assertNotNull(uploader);
    waitForCanUpload(item1);
    CHECK.empty(uploader.getRequestedItems());

    myNotifications.reset();
    uploader = TestUploader.beginUpload(myManager, item1, item2);
    assertNotNull(uploader);
    myNotifications.checkNotEmptyAndReset();
    assertFalse(myManager.canUpload(item2));
    uploader.waitStarted();
    assertFalse(myManager.canUpload(item2));
    assertTrue(myManager.canUpload(item1));
    CHECK.singleElement(item2, uploader.getRequestedItems().toNativeArray());
  }

  public void testDuringUpload() throws ExecutionException, InterruptedException {
    long item = downloadItem(1, "a");
    editItem(item, "local", "a");

    myNotifications.reset();
    TestUploader upload = TestUploader.beginUpload(myManager, item);
    assertNotNull(upload);
    upload.waitStarted();

    myNotifications.checkNotEmptyAndReset();
    assertFalse(myManager.canUpload(item));
    assertNull(TestUploader.beginUpload(myManager, item));
    myNotifications.checkEmpty();

    editItem(item, "local2", "a");
    assertNull(TestUploader.beginUpload(myManager, item));

    mySelector.clear();
    myNotifications.reset();
    upload.finishUpload(item, createValues(1, "local"));
    mySelector.waitProcessed(item);
    flushWriteQueue();
    myNotifications.checkNotEmptyAndReset();
    
    checkWholeTrunk(item, SyncState.EDITED, 1, "local2");
    assertNotNull(TestUploader.beginUpload(myManager, item));
  }

  public void testEditOverlapsUpload() throws ExecutionException, InterruptedException {
    long item = downloadItem(1, "a");
    editItem(item, "local", "a");

    TestUploader upload = TestUploader.beginUpload(myManager, item);
    assertNotNull(upload);
    upload.waitStarted();

    EditControl control = myManager.prepareEdit(item);
    assertNotNull(control);
    control.start(myEditFactory);
    TestEditFactory.MyEditor editor = myEditFactory.waitForEditor();

    upload.finishUpload(item, createValues(1, "local"));
    flushWriteQueue();
    checkWholeTrunk(item, SyncState.EDITED, 1, "local");

    editor.commit(TEXT, "local2");
    editor.waitReleased();

    mySelector.waitProcessed(item);
    flushWriteQueue();
    checkWholeTrunk(item, SyncState.EDITED, 1, "local2");
  }
  
  public void testNotChanged() throws ExecutionException, InterruptedException {
    long item1 = downloadItem(1, "a");
    long item2 = downloadItem(2, "x");
    editItem(item2, "y", "x");

    assertTrue(myManager.canUpload(item1));
    TestUploader upload1 = TestUploader.beginUpload(myManager, item1, item2);
    assertNotNull(upload1);
    waitForCanUpload(item1);
    assertEquals(2, upload1.getSeenItems().size()); // Not changed item is requested but not locked
    
    assertNotNull(TestUploader.beginUpload(myManager, item1));

    mySelector.clear();
    upload1.finishUpload(item1, createValues(1, "b"));
    flushWriteQueue();
    checkWholeTrunk(item1, SyncState.SYNC, 1, "b");
  }

  public void testFailedUpload() throws ExecutionException, InterruptedException {
    long item = createNew("abc");
    TestUploader upload = TestUploader.beginUpload(myManager, item);
    assertNotNull(upload);
    ItemUploader.UploadProcess process = upload.waitForProcess();
    assertNotNull(process);
    assertFalse(myManager.canUpload(item));
    process.uploadDone();
    waitForCanUpload(item);
    checkBase(item, SyncSchema.INVISIBLE, true);
    CHECK.order(queryFailedUploads(BoolExpr.<DP>TRUE()), item);

    upload = TestUploader.beginUpload(myManager, item);
    assertNotNull(upload);
    finishUpload(item, upload, 1, "abcd");
    checkTrunk(item, TEXT, "abcd");
    checkTrunk(item, ID, 1);
    CHECK.empty(queryFailedUploads(BoolExpr.<DP>TRUE()));
  }

  public void testUploadSlave() throws InterruptedException, ExecutionException {
    long master = downloadNew(createValues(1, "a"));
    long slave = createNewSlave(master, null, "b");
    checkSyncState(master, SyncState.SYNC);
    checkSyncState(slave, SyncState.NEW);
    TestUploader upload = TestUploader.beginWithAdditional(myManager, master, slave);
    assertNotNull(upload);
    upload.waitForProcess();
    CHECK.unordered(upload.getSeenItems().toNativeArray(), master, slave);
    CHECK.singleElement(slave, upload.getRequestedItems().toNativeArray());
    upload.finishUpload(slave, createValues(2, "b"));
    flushWriteQueue();
    checkSyncState(slave, SyncState.SYNC);
    checkTrunk(slave, ID, 2);
    checkTrunk(slave, TEXT, "b");
  }

  private long createNewSlave(long master, Integer id, String text) throws InterruptedException {
    AttributeMap map = new AttributeMap();
    map.put(ID, id);
    map.put(TEXT, text);
    map.put(MASTER, master);
    return CommitItemEdit.createNew(myManager, map);
  }

  private LongArray queryFailedUploads(final BoolExpr<DP> expr) throws ExecutionException, InterruptedException {
    return db.readForeground(new ReadTransaction<LongArray>() {
      @Override
      public LongArray transaction(DBReader reader) throws DBOperationCancelledException {
        return SyncUtils.queryFailedUploads(reader.query(expr)).copyItemsSorted();
      }
    }).get();
  }
}
