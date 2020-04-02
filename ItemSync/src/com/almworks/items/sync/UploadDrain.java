package com.almworks.items.sync;

import com.almworks.integers.LongList;

public interface UploadDrain extends DBDrain {
  /**
   * Notifies that item upload is successfully complete. All changes are uploaded. New server state should be written to return value.
   * @param item the item that was uploaded
   * @return version creator to hold new server values
   */
  ItemVersionCreator setAllDone(long item);

  /**
   * Cancel upload of the specified item. The item upload lock is released - items becomes uploadable and downloadable.
   * @param item to cancel upload
   */
  void cancelUpload(long item);

  /**
   * @return items allowed for upload within this upload process.<br>
   * Returned list doesn't include items which are already cancelled or upload is finished (including items which are
   * cancelled or finished within this transaction).
   * @see #cancelUpload(long)
   * @see #setAllDone(long)
   */
  LongList getLockedForUpload();
}
