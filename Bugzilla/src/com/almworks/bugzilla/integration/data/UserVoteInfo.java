package com.almworks.bugzilla.integration.data;

import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;

import java.util.Map;

// @author Alex
public class UserVoteInfo {
  public Map<String, VoteProductInfo> myInfo;
  public boolean myIdFound = false;

  public MultiMap<String, String> collectVoteValuesForUpload() {
    MultiMap<String, String> parameters = new MultiMap<String, String>();
    parameters.add("action", "vote");
    for (VoteProductInfo myVoteProductInfo : myInfo.values()) {
      for (Pair<String, Integer> vote : myVoteProductInfo.myVotes) {
        parameters.add(vote.getFirst(), String.valueOf(vote.getSecond()));
      }
    }
    return parameters;
  }

  @Override
  public String toString() {
    return "UVI[" + myIdFound + ":" + myInfo + "]";
  }
}
