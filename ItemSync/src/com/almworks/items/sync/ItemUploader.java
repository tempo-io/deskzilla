package com.almworks.items.sync;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBResult;
import com.almworks.util.threads.CanBlock;

public interface ItemUploader {
  /**
   * Prepares item set for upload. Checks trunk version and probably adds additional items that has to be
   * uploaded with the given item
   * @param prepare facility to modify set of items which needs upload
   * @param trunk the item requested for upload
   * @param uploadAllowed <b>true</b> means that the item is allowed for upload.<br>
   * <b>false</b> means that the item upload is not allowed (item has no changes or already locked for another upload). The item
   * is passed for investigation purpose only, the uploader may try to upload slaves, or other related items.
   * @see com.almworks.items.sync.ItemUploader.UploadPrepare#addToUpload(long)
   * @see com.almworks.items.sync.ItemUploader.UploadPrepare#addToUpload(com.almworks.integers.LongList)
   */
  void prepare(UploadPrepare prepare, ItemVersion trunk, boolean uploadAllowed);

  /**
   * Once upload preparation is finished the upload process starts. This method is call via Long thread gate
   * and intended to perform upload.<br>
   * Upload may be performed synchronously or asynchronously.<br>
   * Once this method exists without exception the {@link ItemUploader} implementation is responsible to unlock items.
   * If an item is not unlocked due to exception no farther upload (of the item) is possible until application restart.
   * @param process sink for result of upload
   * @see com.almworks.items.sync.ItemUploader.UploadProcess#writeUploadState(DownloadProcedure)
   * @see com.almworks.items.sync.UploadDrain#setAllDone(long)
   */
  @CanBlock
  void doUpload(UploadProcess process);

  interface UploadPrepare {
    /**
     * Mark for upload single item. Item can be uploaded (during write transaction) iff:<br>
     * 1. Has no pending merge, such as not completed download or upload.<br>
     * 2. Has no CONFLICT shadow. No conflicting remote change is known.<br>
     * 3. Is not during upload at the moment.<br>
     * If item has no local changes it doesn't forbid upload, but nothing is going to happen in this case.
     * @param item the item to upload
     * @return true iff item can be uploaded right now (including the item has no local changes)
     */
    boolean addToUpload(long item);

    /**
     * Adds to upload all requested items if all can be uploaded, otherwise no item is added to upload.
     * @param items to add for upload
     * @return true iff all items are successfully added for uploaded. false means no item is added
     * @see #addToUpload(long)
     */
    boolean addToUpload(LongList items);

    /**
     * Cancel upload. After this method is called upload is cancelled, upload locks are released (items becomes uploadable).
     * Method {@link ItemUploader#prepare(com.almworks.items.sync.ItemUploader.UploadPrepare, ItemVersion,boolean)}
     * wont be called any more, {@link com.almworks.items.sync.ItemUploader#doUpload(com.almworks.items.sync.ItemUploader.UploadProcess)}
     * wont be called too.
     */
    void cancelUpload();

    /**
     * Cancel of upload of single item. The item becomes uploadable (lock is released).
     * @param item item to be unlocked and excluded from this upload
     */
    void removeFromUpload(long item);
  }

  interface UploadProcess {
    /**
     * To be called when upload to server is done and upload result has to be stored to DB. The provided
     * {@link com.almworks.items.sync.UploadDrain} allows to update item state and set upload result
     * (what upload steps are successful and failed).
     * @param writer procedure to write new server item state
     * @return DBResult of enquired write transaction.
     */
    DBResult<Object> writeUploadState(DownloadProcedure<? super UploadDrain> writer);

    /**
     * Notifies that upload is finished. Upload of any item not written via {@link #writeUploadState(DownloadProcedure)}
     * is cancelled and corresponding upload lock is released. If actually the item is uploaded the future self conflict
     * is possible.<br>
     * After the method is called {@link #writeUploadState(DownloadProcedure)} still can be used, however
     * the {@link com.almworks.items.sync.UploadDrain} acts as generic download drain.
     */
    void uploadDone();

    void cancelUpload(long item);

    void cancelUpload(LongList items);
  }
}
