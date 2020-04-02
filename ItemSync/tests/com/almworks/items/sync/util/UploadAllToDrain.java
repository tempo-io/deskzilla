package com.almworks.items.sync.util;

import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.*;
import com.almworks.items.util.AttributeMap;
import junit.framework.Assert;

public class UploadAllToDrain implements DownloadProcedure<UploadDrain> {
  private final long myItem;
  private final AttributeMap myUploaded;
  private TestReference<Boolean> myDone = new TestReference<Boolean>();

  public UploadAllToDrain(long item, AttributeMap uploaded) {
    myItem = item;
    myUploaded = uploaded;
  }

  @Override
  public void write(UploadDrain drain) throws DBOperationCancelledException {
    ItemVersionCreator server = drain.setAllDone(myItem);
    server.setAlive();
    SyncUtils.copyValues(server, myUploaded);
  }

  @Override
  public void onFinished(DBResult<?> result) {
    boolean successful = result.isSuccessful();
    Assert.assertTrue(successful);
    Assert.assertTrue(myDone.compareAndSet(null, successful));
  }

  public boolean waitDone() throws InterruptedException {
    return myDone.waitForPublished();
  }
}
