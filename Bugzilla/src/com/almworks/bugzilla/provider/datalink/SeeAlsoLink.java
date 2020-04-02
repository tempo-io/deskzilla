package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import org.jetbrains.annotations.*;

import java.util.List;

public class SeeAlsoLink extends AbstractAttributeLink<List<String>> {
  public SeeAlsoLink(DBAttribute<List<String>> attribute) {
    super(attribute, BugzillaAttribute.SEE_ALSO, false, false);
  }

  public void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context) {
    if (cannotTell(bugInfo)) return;
    List<String> links = bugInfo.getValues().getTupleValues(myBugzillaAttribute);
    bugCreator.setValue(getAttribute(), links.isEmpty() ? null : links);
  }

  protected String getBCElementParameter(OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection) {
    // todo
    return null;
  }

  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    DBAttribute<List<String>> attribute = getWorkspaceAttribute();
    BugzillaAttribute bza = getBugzillaAttribute();

    if (bug.isChanged(attribute)) {
      BugzillaValues bv = info.getNewValues();
      bv.clear(bza);
      List<String> value = bug.getNewerValue(attribute);
      if (value != null) {
        bv.putAll(bza, value);
      } else {
        bv.put(bza, "");
      }
    }

    List<String> value = bug.getElderValue(attribute);
    if (value != null) {
      BugzillaValues bv = info.getPrevValues();
      bv.clear(bza);
      bv.putAll(bza, value);
    }
  }

  @Override
  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return null;
  }
}
