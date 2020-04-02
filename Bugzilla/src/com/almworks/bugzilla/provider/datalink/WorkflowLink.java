package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.items.sync.*;
import com.almworks.util.L;
import org.jetbrains.annotations.*;

public class WorkflowLink implements DataLink {
  private final SingleEnumAttribute myStatusLink;
  private final SingleEnumAttribute myResolutionLink;
  private final SingleEnumAttribute myAssignedToLink;
  private final DuplicateOfLink myDuplicateOfLink;
  // todo - duplicates link

  public WorkflowLink(SingleEnumAttribute statusLink, SingleEnumAttribute resolutionLink, SingleEnumAttribute assignedToLink,
    DuplicateOfLink duplicateOfLink) {
    assert statusLink != null;
    assert resolutionLink != null;
    assert assignedToLink != null;
    assert duplicateOfLink != null;
    myAssignedToLink = assignedToLink;
    myResolutionLink = resolutionLink;
    myStatusLink = statusLink;
    myDuplicateOfLink = duplicateOfLink;
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info)
    throws UploadNotPossibleException
  {
    boolean submit = bug.getNewerVersion().getSyncState() == SyncState.NEW;
    try {
      saveWorkflowAttribute(bug.getElderVersion(), info, myStatusLink, !submit);
      saveWorkflowAttribute(bug.getElderVersion(), info, myResolutionLink, false);
      saveWorkflowAttribute(bug.getElderVersion(), info, myAssignedToLink, false);
    } catch (ReferenceLink.BadReferent e) {
      throw new UploadNotPossibleException(L.content("Current value of " + e.getAttribute().getName() + " is not valid"));
    }
  }

  private void saveWorkflowAttribute(ItemVersion lastServerRevision, BugInfoForUpload info, SingleEnumAttribute enumAttr,
    boolean mandatory) throws ReferenceLink.BadReferent, UploadNotPossibleException {

    String currentStatus = enumAttr.getLocalStringForBug(lastServerRevision);
    if (currentStatus == null && mandatory)
      throw new UploadNotPossibleException(L.content("No current valid " + enumAttr.getBugzillaAttribute()));
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    // todo let integration save in update info - what can be checked?
    return null;
  }

  // =============================== EMPTY ==========================================
  // These methods are empty
  
  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }

  //  public void onCloseLocalChain(RevisionCreator revisionCreator, Transaction transaction) {
//  }

  // UPLOADING
  public void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo,
    @NotNull BugzillaContext context) {
  }
}
