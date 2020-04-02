package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.FieldIntConstraint;
import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.EngineUtils;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SimpleAutoMerge;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Pair;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.List;

import static com.almworks.bugzilla.provider.datalink.schema.Bug.BUG_NS;
import static com.almworks.bugzilla.provider.datalink.schema.Product.NS;

public class VotesLink implements DataLink, RemoteSearchable {
  //this attribute stores list of reference (typeUser)
  public static final DBAttribute<List<Long>> votesUserList =
    BUG_NS.linkList("votesUserList", "Votes List (Users)", true);

  // this attribute stores int array of vote values
  public static final DBAttribute<List<Integer>> votesValueList =
    BUG_NS.integerList("votesValueList", "Votes List (Values)", true);

  // keeps our vote for current bug
  public static final DBAttribute<Integer> votesByUser = BUG_NS.integer("votesByUser", "Votes(my)", true);

  // total votes for bug
  public static final DBAttribute<Integer> votes = BUG_NS.integer("votes", "Votes", true);

  /**
   * Maximum votes (sum of votes for every bug in product)
   * that user can use in given product. It is attribute of product artifact
   */
  public static final DBAttribute<Integer> productMaxVotes = NS.integer("maxVotesPerProduct", "Max Votes", false);

  //Maximum votes for one bug in current product. It is attribute of product artifact
  public static final DBAttribute<Integer> productMaxVotesPerBug = NS.integer("maxVotesPerBug", "Max Votes Per Bug", false);

  //Used in product votes
  public static final DBAttribute<Integer> productVotesUsed = NS.integer("votesUsedInProduct", "Votes Used", false);

  public static final ItemAutoMerge AUTO_MERGE = new MyAutoMerge();

  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context)
  {
    int totalVotes = Util.toInt(bugInfo.getValues().getScalarValue(BugzillaAttribute.TOTAL_VOTES, "0"), 0);
    Integer oldVotes = bugCreator.getValue(votes);
    bugCreator.setValue(votes, totalVotes);
    if (totalVotes <= 0) {
      // total votes == 0, therefore clean other attributes
      bugCreator.setValue(votesByUser, 0);
      bugCreator.setValue(votesUserList, (List<Long>)null);
      bugCreator.setValue(votesValueList, null);
      return;
    }
    List<Pair<BugzillaUser, Integer>> votes = bugInfo.getVotes();
    if (votes == null) {
      return;
    }
    List<Long> userList = Collections15.arrayList(votes.size());
    List<Integer> valList = Collections15.arrayList(votes.size());
    int sum = 0;
    int userVotes = 0;
    long me = EngineUtils.getConnectionUser(context.getConnection(), bugCreator.getReader());
    for (Pair<BugzillaUser, Integer> vote : votes) {
      BugzillaUser bugzillaUser = vote.getFirst();
      Integer value = vote.getSecond();
      if (bugzillaUser == null || value == null) {
        Log.warn(this + ": bad votes [" + vote + "]");
        continue;
      }
      long user = User.getOrCreate(bugCreator, bugzillaUser, pm);
      if (user <= 0) {
        Log.warn(this + ": cannot find user for vote [" + vote + "]");
        continue;
      }

      if (user == me) {
        userVotes = value;
      }

      sum += value;
      userList.add(user);
      valList.add(value);
    }
    assert valList.size() == userList.size();
    if (sum != totalVotes) {
      Log.warn(this + ": votes mismatch: " + totalVotes + " " + sum);
    }
    bugCreator.setValue(votesByUser, userVotes);
    bugCreator.setValue(votesValueList, valList);
    bugCreator.setValue(votesUserList, userList);
  }

  @Nullable
  @CanBlock
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
    if (type == null)
      return null;
    if (votes.equals(constraint.getAttribute()))
      return null;
    String value = getBCElementParameter(constraint);
    if (value == null)
      return null;
    Pair<String, BooleanChartElementType> fixedPair = LinkUtil.fixEmptyBooleanChartCondition(value, type);
    if (fixedPair != null) {
      value = fixedPair.getFirst();
      type = fixedPair.getSecond();
      if (type == null || value == null)
        return null;
    }
    return BooleanChart.createElement(BugzillaAttribute.TOTAL_VOTES, type, value);
  }

  private static String getBCElementParameter(OneFieldConstraint constraint) {
    if (constraint instanceof FieldIntConstraint) {
      BigDecimal value = ((FieldIntConstraint) constraint).getIntValue();
      return value == null ? null : value.toString();
    }
    return null;
  }

  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) throws UploadNotPossibleException
  {
    if(bug.isChanged(votesByUser)) {
      Integer value = bug.getNewerValue(votesByUser);
      if (value != null) {
        info.setVoteValue(value);
      }
    }
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return null;
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }

  public static void updateProduct(ItemVersionCreator creator, int totalVotes, int usedVotes, int votesPerBug) {
    creator.setValue(productMaxVotes, totalVotes);
    creator.setValue(productMaxVotesPerBug, votesPerBug);
    creator.setValue(productVotesUsed, usedVotes);
  }

  private static class MyAutoMerge extends SimpleAutoMerge {
    @Override
    public void resolve(AutoMergeData data) {
      Integer baseVotes = data.getLocal().getElderValue(votesByUser);
      Integer localVotes = data.getLocal().getNewerValue(votesByUser);
      if (localVotes == null || localVotes.equals(baseVotes)) {
        // no local changes -
        data.discardEdit(votes);
        data.discardEdit(votesByUser);
        data.discardEdit(votesUserList);
        data.discardEdit(votesValueList);
        return;
      }
      // try merge if my votes were not changed
      Integer remoteVotes = data.getServer().getNewerValue(votesByUser);
      if (remoteVotes == null || !remoteVotes.equals(baseVotes)) {
        // conflict! remove lists and total
        data.setResolution(votes, null);
        data.setResolution(votesUserList, null);
        data.setResolution(votesValueList, null);
        return;
      }
      // merge
      // calculate total votes based on local diff
      data.setResolution(votesByUser, localVotes);
      Integer remoteTotalVotes = data.getServer().getNewerValue(votes);
      if (remoteTotalVotes == null) {
        data.setResolution(votes, null);
      } else {
        data.setResolution(votes, remoteTotalVotes - Util.NN(baseVotes, 0) + localVotes);
      }
      List<Long> userList = data.getServer().getNewerValue(votesUserList);
      List<Integer> valueList = data.getServer().getNewerValue(votesValueList);
      if (userList == null || valueList == null || userList.size() != valueList.size()) {
        if (remoteVotes == 0) {
          userList = Collections15.arrayList();
          valueList = Collections15.arrayList();
        } else {
          data.setResolution(votesUserList, null);
          data.setResolution(votesValueList, null);
          return;
        }
      } else {
        // writable copies
        userList = Collections15.arrayList(userList);
        valueList = Collections15.arrayList(valueList);
      }
      // merge lists
      mergeLists(data, userList, valueList, localVotes);
    }

    private static void mergeLists(AutoMergeData data, List<Long> userList, List<Integer> valueList, Integer localVotes) {
      long me = getMe(data);
      if (me <= 0) {
        data.setResolution(votesUserList, null);
        data.setResolution(votesValueList, null);
        return;
      }
      int meIndex = userList.indexOf(me);
      if (meIndex < 0) {
        if (localVotes > 0) {
          userList.add(me);
          valueList.add(localVotes);
        }
      } else {
        if (localVotes <= 0) {
          userList.remove(meIndex);
          valueList.remove(meIndex);
        } else {
          valueList.set(meIndex, localVotes);
        }
      }
      data.setResolution(votesUserList, userList);
      data.setResolution(votesValueList, valueList);
    }

    private static long getMe(AutoMergeData data) {
      DBReader reader = data.getReader();
      return Util.NN(reader.getValue(
        Util.NN(reader.getValue(data.getItem(), SyncAttributes.CONNECTION), 0L),
        Connection.USER
      ), 0L);
    }
  }
}
