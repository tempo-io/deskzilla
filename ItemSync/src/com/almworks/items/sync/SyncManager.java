package com.almworks.items.sync;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.api.DBResult;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.properties.Role;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

public interface SyncManager {
  Role<SyncManager> ROLE = Role.role("ItemSync.SyncManager");
  Role<Modifiable> MODIFIABLE = Role.role("ItemSync.SyncManager.MODIFIABLE");

  /**
   * @return lock for edit if the item is currently locked, null if the item is not locked by any editor
   */
  @Nullable
  EditorLock findLock(long item);

  /**
   * Usacase: check that no item is locked for edit right now
   * @return lock for first item locked for edit
   * @see #findLock(long)
   */
  @Nullable
  EditorLock findAnyLock(LongList items);

  /**
   * Prepares edit control. Checks that no item is locked right now.
   * @param items items to edit. To be locked when edit starts
   * @return edit operation control. null means that at least one item is now locked by other editor. Edit can not be
   * started until lock is released (by finish or cancel edit)
   */
  @Nullable
  EditControl prepareEdit(LongList items);

  /**
   * @see #prepareEdit(com.almworks.integers.LongList)
   */
  @Nullable
  EditControl prepareEdit(long item);

  /**
   * Simplified edit: if editor does not need any locks, it can call this method directly.
   * Locks are not needed if a new item is created, or if only non-uploadable attributes are changed.<br>
   * This method is intended to simplify item creation and modifications of not uploadable attributes.
   * Attempt to change uploadable attributes from within commit will lead to runtime exception.<br>
   * @param commit commit procedure
   * @see #prepareEdit(com.almworks.integers.LongList)
   */
  void commitEdit(EditCommit commit);

  /**
   * Performs edit commit without locks. If an item is locked by other editor the editor is notified via
   * {@link com.almworks.items.sync.ItemEditor#onItemsChanged(gnu.trove.TLongObjectHashMap)} when write transaction
   * successfully finishes.<br>
   * Should be used to edit only attributes that can be automatically merged by other editors.
   * @param commit commit procedure. Any change to any item is allowed, but if concurrent editor failed to merge changes
   * it may overwrite it. 
   */
  void unsafeCommitEdit(EditCommit commit);

  /**
   * Simplified edit with locks: if editor does not show any windows, it can call this method directly.
   * Attempts to take locks for the specified items, if succeeds, performs edit. If locks are unavailable, returns immediately.
   * @param items items to edit
   * @param commit commit procedure
   * @return true iff commit has started
   */
  boolean commitEdit(LongList items, EditCommit commit);

  /**
   * @see #upload(com.almworks.integers.LongList, ItemUploader)
   */
  @Nullable
  Lifespan upload(long item, ItemUploader uploader);

  /**
   * Initiates upload of the given items using given upload service.
   * @param items items to upload
   * @param uploader upload service
   * @return null if upload surely not possible right now. Otherwise, a lifespan that detaches after upload is complete -- with failure or success.
   */
  @Nullable
  Lifespan upload(LongList items, ItemUploader uploader);

  DBResult<Object> writeDownloaded(DownloadProcedure<? super DBDrain> procedure);

  /**
   * Checks if the item not locked for upload now
   * @param item item to check
   * @return true if the item is not uploading. In spite of this method returns true, item may be not uploaded due to
   * no changes, conflict or another upload is just started.
   */
  boolean canUpload(long item);

  /** @return true if all of the specified items are not locked for upload currently. Same remarks as in {@link #canUpload} are applicable here. */
  boolean canUploadAll(LongList items);

  /**
   * @return true if the item is locked for upload, false means that item is not locked for upload.
   */
  boolean isDuringUpload(DBReader reader, long item);

  /**
   * Allows listen to lock changes. Fires event when item is locked/released for edit or for upload.
   * @return Modifiable to listen to
   */
  Modifiable getModifiable();
}
