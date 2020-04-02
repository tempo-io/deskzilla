package com.almworks.bugzilla.integration.data;

import com.almworks.util.Pair;

import java.util.List;

/**
 * @author Alex
*/
public class VoteProductInfo {
  public int myUsed;
  public int myTotal;
  public int myVotePerBug;
  public List<Pair<String, Integer>> myVotes;

  @Override
  public String toString() {
    return "VPI[" + myUsed + ":" + myTotal + ":" + myVotePerBug + ":" + myVotes + "]";
  }
}
