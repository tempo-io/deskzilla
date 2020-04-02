package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.items.sync.*;
import org.jetbrains.annotations.*;

public class Link implements DataLink {
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws UploadNotPossibleException
  {
    prepare.addToUpload(bug.getNewerVersion().getSlaves(Flags.AT_FLAG_MASTER));
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return null;
  }

  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context)
  {
    SingleReferenceLink<String> componentLink = SingleEnumAttribute.COMPONENT.getRefLink();
    String componentId = componentLink.getRemoteString(bugInfo, pm);
    long component = componentId != null ? componentLink.getOrCreateReferent(pm, componentId, bugCreator) : 0;
    new FlagUpdate(bugCreator, component, bugInfo, context).update();
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }
}
