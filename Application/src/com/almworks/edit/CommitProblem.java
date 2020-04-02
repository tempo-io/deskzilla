package com.almworks.edit;

import com.almworks.api.application.ModelKey;

/**
 * @author : Dyoma
 */
class CommitProblem {
  private final String myDescription;

  public CommitProblem(String description) {
    myDescription = description;
  }

  public static CommitProblem invalidValue(ModelKey key, String value) {
    return new CommitProblem("Invalid value for " + key.getDisplayableName() + ": " + value);
  }

  public static CommitProblem general(String description) {
    return new CommitProblem(description);
  }

  public String toString() {
    return myDescription;
  }
}
