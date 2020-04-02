package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelKey;

import java.util.List;

/**
 * @author Alex
 */

// todo review BugzillaDropChangeAction, add voters
public class BugzillaVoteKeys {
  public final ModelKey<List<ItemKey>> votesUserList;
  public final ModelKey<List<Integer>> votesValueList;
  public final ModelKey<Integer> votesMy;
  public final ModelKey<Integer> votes;
  public final ModelKey<Integer> productMaxVotesPerBug;
  public final ModelKey<Integer> productMaxVotes;

  public BugzillaVoteKeys(ModelKey<List<ItemKey>> voterList, ModelKey<List<Integer>> votesValues,
    ModelKey<Integer> ourVote, ModelKey<Integer> totalBugVotes, ModelKey<Integer> votesPerBug,
    ModelKey<Integer> votes)
  {
    votesUserList = voterList;
    votesValueList = votesValues;
    votesMy = ourVote;
    this.votes = totalBugVotes;
    productMaxVotesPerBug = votesPerBug;
    productMaxVotes = votes;
  }
}
