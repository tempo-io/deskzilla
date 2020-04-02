package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.engine.EngineUtils;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaValues;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPNotNull;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

public class MatchDownloadedBugs {
  private final PrivateMetadata myPM;
  private final DBDrain myDrain;
  private final Collection<BugBox> myLeftBoxes;
  private static final BoolExpr<DP> NO_ID = DPNotNull.create(Bug.attrBugID).negate();

  public MatchDownloadedBugs(PrivateMetadata pm, DBDrain drain, Collection<BugBox> boxes) {
    myDrain = drain;
    myPM = pm;
    myLeftBoxes = Collections15.arrayList(boxes);
  }

  public void perform() {
    removeWithItem();
    matchKnown();
    if (myLeftBoxes.isEmpty()) return;
    matchJustUploaded();
    for (BugBox box : myLeftBoxes) {
      assert box.getItem() <= 0;
      long bugItem = Bug.createBugProxy(myPM.thisConnectionItem(), box.getID()).findOrCreate(myDrain);
      if (bugItem <= 0) Log.error("Failed to create bug");
      box.setItem(bugItem);
    }

  }

  private void matchJustUploaded() {
    DBReader reader = getReader();
    DBFilter connectionBug = myPM.getBugsView();
    DBQuery bugsQuery = connectionBug.query(reader);
    long thisUserItem = EngineUtils.getConnectionUser(myPM.thisConnection, reader);
    if (thisUserItem <= 0) return;
    ItemVersion thisUser = SyncUtils.readTrunk(getReader(), thisUserItem);
    DBQuery query = SyncUtils.queryFailedUploads(bugsQuery.query(NO_ID));
    matchQuery(thisUser, query, true);
    BoolExpr<DP> newBugs =
      DPNotNull.create(SyncAttributes.BASE_SHADOW).negate().and(NO_ID).and(connectionBug.getExpr());
    matchQuery(thisUser, getReader().query(newBugs), false);
  }

  private void matchQuery(ItemVersion thisUser, DBQuery query, boolean errorMultiCandidates) {
    List<ItemVersion> candidates = SyncUtils.readItems(getReader(), query.copyItemsSorted());
    for (Iterator<BugBox> it = myLeftBoxes.iterator(); it.hasNext();) {
      BugBox box = it.next();
      BugzillaValues values = box.getBugInfo().getValues();
      String reporter = values.getScalarValue(BugzillaAttribute.REPORTER, null);
      // reporter may be null if this box contains no values, just error
      if (reporter == null || !User.equalId(thisUser, reporter)) continue;
      String summary = (String) values.getValue(BugzillaAttribute.SHORT_DESCRIPTION);
      List<ItemVersion> sameSummary = SyncUtils.selectItems(candidates, Bug.attrSummary, summary);
      if (sameSummary.isEmpty()) continue;
      long product = findProduct(box);
      if (product <= 0) continue;
      List<ItemVersion> sameProduct = SyncUtils.selectItems(sameSummary, Bug.attrProduct, product);
      if (sameProduct.isEmpty()) continue;
      if (sameProduct.size() > 1) {
        if (errorMultiCandidates) Log.error("Several candidates known");
      } else {
        box.setItem(sameProduct.get(0).getItem());
        it.remove();
      }
    }
  }

  private DBReader getReader() {
    return myDrain.getReader();
  }

  private long findProduct(BugBox box) {
    BugzillaValues values = box.getBugInfo().getValues();
    String productId = values.getMandatoryScalarValue(BugzillaAttribute.PRODUCT);
    return Product.ENUM_PRODUCTS.findById(getReader(), myPM.getConnectionRef(), productId);
  }

  private void matchKnown() {
    DBReader reader = getReader();
    for (Iterator<BugBox> it = myLeftBoxes.iterator(); it.hasNext();) {
      BugBox box = it.next();
      long item = Bug.createBugProxy(myPM.thisConnectionItem(), box.getID()).findItem(reader);
      if (item > 0) {
        box.setItem(item);
        it.remove();
      }
    }
  }

  private void removeWithItem() {
    for (Iterator<BugBox> it = myLeftBoxes.iterator(); it.hasNext();) {
      BugBox box = it.next();
      if (box.getItem() > 0) it.remove();
    }
  }
}
