package com.almworks.bugzilla.provider.sync;

import com.almworks.api.application.ItemDownloadStage;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.FrontPageData;
import com.almworks.items.sync.ItemUploader;
import com.almworks.util.L;
import org.almworks.util.Log;
import org.almworks.util.Util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * BugBox holds information about one bug during the synchronization. Any or all of the fields could be
 * null, so you should not take any assumptions.
 *
 * @author sereda
 */
public class BugBox {
  private static final int MAXIMUM_UPLOAD_COUNT = 3;

  private long myItem;

  private BugInfo myInfo;

  private BugInfo.ErrorType myError;

  private FrontPageData myFrontPageData;

  private ItemDownloadStage myDownloadStage;

  /**
   * Bug ID. Usually not null unless bug is yet to be submitted.
   */
  private Integer myID;

  /**
   * Activity loaded from server
   */
//  private List<ChangeSet> myActivity;

  /**
   * Type of synchronization requested
   */
  private SyncType mySyncType;

  private final AtomicReference<ItemUploader.UploadProcess> myUploadProcess = new AtomicReference<ItemUploader.UploadProcess>();

  private int myUploadCount = 0;
  private boolean myProblematic = false;

  public BugBox(Integer id, SyncType syncType) {
    myID = id;
    mySyncType = syncType;
  }

  public BugBox(long item, SyncType syncType) {
    myItem = item;
    mySyncType = syncType;
  }

  public synchronized void clearDownloadedInfo() {
    myError = null;
    myFrontPageData = null;
    myInfo = null;
  }

  public synchronized long getItem() {
    return myItem;
  }

  public synchronized void setItem(long item) {
    myItem = item;
//    Debug.__out("+++ setting " + artifact + " for " + this);
  }

  public synchronized BugInfo.ErrorType getError() {
    return myError;
  }

  public synchronized void setError(BugInfo.ErrorType error) {
    myError = error;
  }

  public synchronized FrontPageData getFrontPageData() {
    return myFrontPageData;
  }

  public synchronized void setFrontPageData(FrontPageData data) {
    myFrontPageData = data;
  }

  public synchronized Integer getID() {
    return myID;
  }

  public void setID(Integer ID) {
    myID = ID;
  }

  public synchronized BugInfo getBugInfo() {
    return myInfo;
  }

  public synchronized void setInfoLight(BugInfo info) {
    myInfo = info;
  }

  public synchronized SyncType getSyncType() {
    return mySyncType;
  }

  public synchronized void updateSyncType(Integer id, SyncType syncType) {
    assert Util.equals(myID, id);
    mySyncType = SyncType.heaviest(mySyncType, syncType);
  }

  public synchronized void incrementUploadCount() throws ConnectorException {
    myUploadCount++;
    if (myUploadCount > MAXIMUM_UPLOAD_COUNT)
      throw new ConnectorException("upload count " + myUploadCount, L.content("Upload failed"), L.content(
        "We were not able to upload this bug to server. We have tried \n" +
          "several times, but each time server signalled a conflict or was not \n" +
          "able to process our request.\n\n" +
          "Please check that you are able to update this bug using web interface."));
  }

  public synchronized void setProblematic() {
    myProblematic = true;
  }

  public synchronized boolean isProblematic() {
    return myProblematic;
  }

  public String toString() {
    return "BB(" + myID + ":" + myItem + ")";
  }

  public void setDownloadStage(ItemDownloadStage downloadStage) {
    myDownloadStage = downloadStage;
  }

  public ItemDownloadStage getDownloadStage() {
    return myDownloadStage;
  }

  public void uploadAllowed(ItemUploader.UploadProcess process) {
    if (!myUploadProcess.compareAndSet(null, process)) Log.error("Uploaded twice");
  }

  public ItemUploader.UploadProcess getUploadProcess() {
    ItemUploader.UploadProcess process = myUploadProcess.get();
    if (process == null) Log.error("Upload not allowed");
    return process;
  }
}
