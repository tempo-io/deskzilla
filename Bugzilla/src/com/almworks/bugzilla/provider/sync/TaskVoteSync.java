package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.HttpFailureConnectionException;
import com.almworks.api.engine.SyncParameter;
import com.almworks.api.engine.SyncParameters;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.UserVoteInfo;
import com.almworks.bugzilla.integration.data.VoteProductInfo;
import com.almworks.bugzilla.integration.err.BugzillaErrorException;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.VotesLink;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.*;
import com.almworks.util.L;
import com.almworks.util.Pair;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * @author Alex
 */

public class TaskVoteSync extends LinearProgressTask {
  public TaskVoteSync(SyncController synchronization) {
    super(synchronization, "my-votes-load", L.progress("Loading votes"), 5000, true);
  }

  protected void doRun() throws ConnectorException, InterruptedException {
    BugzillaIntegration bugzillaIntegration = getIntegration();
    UserVoteInfo voteInfo;
    boolean votingDisabled = false;
    try {
      voteInfo = bugzillaIntegration.loadVotesDefaults();
    } catch(BugzillaErrorException e) {
      if(BugzillaErrorDetector.isExtensionDisabled(e)) {
        Log.warn("Voting disabled");
        voteInfo = null;
        votingDisabled = true;
      } else {
        throw e;
      }
    } catch(HttpFailureConnectionException e) {
      if(e.getStatusCode() == 404) {
        Log.warn("Cannot load vote info, 404");
        voteInfo = null;
      } else {
        throw e;
      }
    }
    if (voteInfo != null) {
      getContext().getActor(SyncManager.ROLE).writeDownloaded(new UpdateVotes(voteInfo, this)).waitForCompletion();
    }
    getContext().getOptionalFieldsTracker().reportUnused(BugzillaAttribute.TOTAL_VOTES, votingDisabled);
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return syncParameters.get(SyncParameter.ALL_ITEMS) != null;
  }

  private static class UpdateVotes implements DownloadProcedure<DBDrain> {
    private final UserVoteInfo myVoteInfo;
    private final Task myTask;

    public UpdateVotes(UserVoteInfo voteInfo, Task task) {
      myVoteInfo = voteInfo;
      myTask = task;
    }

    @Override
    public void write(DBDrain drain) throws DBOperationCancelledException {
      try {
        updateProductVoteParams(drain);
        updateOurVotes(drain);
      } catch (CancelledException e) {
        throw new DBOperationCancelledException();
      }
    }


    private void updateOurVotes(DBDrain drain) {
      Map<String, VoteProductInfo> voteProductInfoMap = myVoteInfo.myInfo;
      long connection = myTask.getPrivateMetadata().thisConnectionItem();
      for (VoteProductInfo voteProductInfo : voteProductInfoMap.values()) {
        for (Pair<String, Integer> vote : voteProductInfo.myVotes) {
          ItemProxy bug = Bug.createBugProxy(connection, vote.getFirst());
          ItemVersionCreator creator = drain.changeItem(bug);
          creator.setValue(VotesLink.votesByUser, vote.getSecond());
        }
      }
    }

    private void updateProductVoteParams(DBDrain drain) throws CancelledException {
      PrivateMetadata pm = myTask.getPrivateMetadata();
      LongArray products =
        SingleEnumAttribute.PRODUCT.getRefLink().getReferentsView(pm).query(drain.getReader()).copyItemsSorted();
      for (int i = 0; i < products.size(); i++) {
        long p = products.get(i);
        ItemVersion product = drain.forItem(p);
        myTask.checkCancelled();
        final String productName = Util.NN(Product.ENUM_PRODUCTS.getStringId(product)).trim();
        if (productName.length() == 0) {
          Log.warn("product with no name " + product);
          continue;
        }

        ItemVersionCreator creator = drain.changeItem(product.getItem());
        VoteProductInfo voteProductInfo = myVoteInfo.myInfo.get(productName);
        if (voteProductInfo != null) updateDefaultVoteInfo(creator, voteProductInfo);
      }
    }

    private static void updateDefaultVoteInfo(ItemVersionCreator product, @NotNull VoteProductInfo information) {
      CommonMetadata.votesLink.updateProduct(product, information.myTotal, information.myUsed, information.myVotePerBug);
    }

    @Override
    public void onFinished(DBResult<?> result) {
    }
  }
}