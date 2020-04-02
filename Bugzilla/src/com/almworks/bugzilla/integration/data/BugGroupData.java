package com.almworks.bugzilla.integration.data;

import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public class BugGroupData {
  private final String myFormId;
  private final String myName;
  private final String myDescription;

  public BugGroupData(@NotNull String formId, @Nullable String name, @NotNull String description) {
    myFormId = formId;
    myName = name;
    myDescription = description;
  }

  @NotNull
  public String getFormId() {
    return myFormId;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public String toString() {
    return myFormId + ":" + myName + ":" + myDescription;
  }

  @Override
  public boolean equals(Object o) {
    if(this == o) {
      return true;
    }
    if(o == null || getClass() != o.getClass()) {
      return false;
    }
    final BugGroupData that = (BugGroupData) o;
    return Util.equals(myFormId, that.getFormId())
      && Util.equals(myName, that.getName())
      && Util.equals(myDescription, that.getDescription());
  }

  @Override
  public int hashCode() {
    int result = myFormId != null ? myFormId.hashCode() : 0;
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    return result;
  }
}
