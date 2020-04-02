package com.almworks.items.sync.edit;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.TestReference;
import com.almworks.items.sync.util.UploadAllToDrain;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.collections.LongSet;
import gnu.trove.TLongObjectHashMap;
import junit.framework.Assert;
import org.jetbrains.annotations.*;

class TestUploader implements ItemUploader {
  private final TestReference<UploadProcess> myProcess = new TestReference<UploadProcess>();
  private final LongList myItems;
  private final LongList myAdditional;
  private final TLongObjectHashMap<AttributeMap> myUploadValues = new TLongObjectHashMap<>();
  private final LongSet mySeenItems = new LongSet();

  private TestUploader(LongList items, LongList additional) {
    myItems = items;
    myAdditional = additional;
  }

  public static void uploadItem(SyncManager manager, long item) throws InterruptedException {
    TestUploader uploader = beginUpload(manager, item);
    Assert.assertNotNull(uploader);
    UploadProcess process = uploader.waitForProcess();
    AttributeMap values = uploader.myUploadValues.get(item);
    Assert.assertNotNull(values);
    UploadAllToDrain completer = new UploadAllToDrain(item, values);
    process.writeUploadState(completer);
    Assert.assertTrue(completer.waitDone());
  }

  @Nullable
  public static TestUploader beginUpload(SyncManager manager, long ... items) {
    LongArray itemList = LongArray.create(items);
    TestUploader uploader = new TestUploader(itemList, LongList.EMPTY);
    boolean allowed = manager.upload(itemList, uploader) != null;
    return allowed ? uploader : null;
  }

  public static TestUploader beginWithAdditional(SyncManager manager, long original, long ... additional) {
    LongArray items = LongArray.singleton(original);
    TestUploader uploader = new TestUploader(items, LongArray.create(additional));
    boolean allowed = manager.upload(items, uploader) != null;
    return allowed ? uploader : null;
  }

  public UploadAllToDrain finishUpload(final long item, final AttributeMap uploaded) throws InterruptedException {
    UploadProcess process = waitForProcess();
    Assert.assertNotNull(process);
    UploadAllToDrain completer = new UploadAllToDrain(item, uploaded);
    process.writeUploadState(completer);
    return completer;
  }

  @Override
  public void prepare(UploadPrepare prepare, ItemVersion trunk, boolean uploadAllowed) {
    long item = trunk.getItem();
    Assert.assertTrue(item + " in " + myItems, myItems.contains(item) || myAdditional.contains(item));
    Assert.assertNull(myUploadValues.get(item));
    Assert.assertFalse(mySeenItems.contains(item));
    mySeenItems.add(item);
    if (myItems.contains(item)) {
      if (!prepare.addToUpload(myAdditional)) return;
    }
    if (uploadAllowed) myUploadValues.put(item, trunk.getAllShadowableMap());
  }

  @Override
  public void doUpload(UploadProcess process) {
    Assert.assertTrue(myProcess.compareAndSet(null, process));
  }

  public boolean waitStarted() throws InterruptedException {
    return waitForProcess() != null;
  }

  public UploadProcess waitForProcess() throws InterruptedException {
    return myProcess.waitForPublished();
  }

  public LongList getRequestedItems() {
    return LongArray.create(myUploadValues.keys());
  }

  public LongList getSeenItems() {
    return mySeenItems;
  }
}
