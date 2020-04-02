package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.OurConfiguration;
import com.almworks.util.L;
import com.almworks.util.Pair;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressSource;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.List;
import java.util.Set;

public class TaskFlagSync extends TwoStepsTask {
  // DZO-703 uncomment
  //  private Pair<List<String>, List<Integer>> myAttachmentFlags;
  private boolean myHasBugFlags = false;

  public TaskFlagSync(SyncController controller) {
    super(controller, "flags-load", L.progress("Loading flags"), 10000, true);
    setSecondTask(new LoadBugsAndAttachments(controller));
  }

  protected void doRun() throws ConnectorException, InterruptedException {
    BugzillaIntegration bugzillaIntegration = getIntegration();
    OurConfiguration config = getContext().getConfiguration().getValue();
    String[] products = config.getLimitingProducts();
//    String singleProduct = products != null && products.length == 1 ? products[0] : null;
    Set<Integer> sampleBugs = Collections15.hashSet();
    if (products == null || products.length == 0) {
      if (!loadSampleBugIds(bugzillaIntegration, sampleBugs, null))
        return;
    } else {
      for (String product : products) {
        if (!loadSampleBugIds(bugzillaIntegration, sampleBugs, product))
          return;
      }
    }

    myHasBugFlags = !sampleBugs.isEmpty();
    for (Integer bugId : sampleBugs) {
      getSyncData().updateBox(bugId, SyncType.RECEIVE_FULL);
    }
    // DZO-703 uncomment
//    myAttachmentFlags = idsToLoad.getSecond();
    transfer();
  }

  private boolean loadSampleBugIds(BugzillaIntegration bugzillaIntegration, Set<Integer> sampleBugs,
    String singleProduct) throws ConnectorException
  {
    Log.debug("flags: loading sample bugs for product " + singleProduct);
    Pair<Pair<List<String>, List<Integer>>, ?> idsToLoad = bugzillaIntegration.loadRequestPage(singleProduct);
    if (idsToLoad == null) {
      Log.warn("No flag info loaded. Flags metainfo not loaded at all");
      myProgress.setDone();
      return false;
    }
    Pair<?, List<Integer>> f = idsToLoad.getFirst();
    if (f != null && f.getSecond() != null) {
      sampleBugs.addAll(f.getSecond());
    }
    return true;
  }

  //   DZO-703 uncomment
  private class LoadBugsAndAttachments extends Task {
    private final Progress myProgress;

    protected LoadBugsAndAttachments(SyncController controller) {
      super(controller, "flag-bug-attachment-load", L.progress("Loading flags"), 15000);
      myProgress = new Progress(myName);
    }

    public ProgressSource getProgress() {
      return myProgress;
    }

    protected void doRun() throws ConnectorException, InterruptedException {
      updateHasFlags();
      // DZO-703 uncomment
//      Set<String> flagsLeft = Collections15.hashSet(myAttachmentFlags.getFirst());
//      int initialCount = flagsLeft.size();
//      double step = 1d / initialCount;
//      for (Integer attachmentId : myAttachmentFlags.getSecond()) {
//        int leftCount = flagsLeft.size();
//        myProgress.setProgress(((double) initialCount - leftCount) / initialCount, "Loading attachment flags (" + leftCount + " left)");
//        List<FrontPageData.FlagInfo> page;
//        try {
//          page = myController.getData().getIntegration().loadAttachmentPage(attachmentId);
//        } catch (ConnectorException e) {
//          myProgress.addError(e.getShortDescription());
//          continue;
//        }
//        if (page == null || page.isEmpty()) continue;
//        Collection<String> flagNames = updateAttachmentFlags(attachmentId, page);
//        flagsLeft.removeAll(flagNames);
//        if (flagsLeft.isEmpty()) break;
//      }
      myProgress.setDone();
    }

    private void updateHasFlags() {
      BugzillaConnection connection = (BugzillaConnection) getContext().getConnection();
      connection.updateHasFlags(myHasBugFlags);
    }

//    private Collection<String> updateAttachmentFlags(Integer attachmentId, final List<FrontPageData.FlagInfo> page) {
//      List<String> names = Collections15.arrayList();
//      for (FrontPageData.FlagInfo info : page) {
//        String name = info.getName();
//        if (name != null && !name.contains(name))
//          names.add(name);
//      }
//      WorkspaceUtils.repeatUntilNoCollisions(5, new DatabaseRunnable() {
//        public void run() throws CollisionException {
//          BugzillaContext context = myController.getData().getContext();
//          Workspace workspace = context.getWorkspace();
//          Transaction transaction = workspace.beginTransaction();
//          FlagUpdate updater = new FlagUpdate(context, workspace, transaction);
//          try {
//            updater.updateAttachmentTypes(page);
//          } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            transaction.rollback();
//            return;
//          }
//          transaction.commit();
//        }
//      });
//      return names;
//    }
  }
}
