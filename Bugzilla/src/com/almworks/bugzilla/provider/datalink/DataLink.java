package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.sync.*;
import org.jetbrains.annotations.*;

public interface DataLink {
  // MISCELLANEOUS
//  SingletonCollection getSingletons();

  /**
   * @return true if revision was significantly changed
   * todo either void (unused now) or proper return value
   */
  void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context);

  // UPLOADING
//  boolean detectUploadableChanges(Map<ArtifactPointer, Value> changes, Revision lastServerRevision);

  void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws UploadNotPossibleException;

  /**
   * @return text about what was not updated; null if all ok 
   */
  String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata);

  void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm);

  /**
   * see DZO-817 for probable problems
   */
//  void onCloseLocalChain(RevisionCreator revisionCreator, Transaction transaction);
}
