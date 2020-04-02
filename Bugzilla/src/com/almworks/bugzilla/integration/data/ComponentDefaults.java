package com.almworks.bugzilla.integration.data;

import org.jetbrains.annotations.*;

import java.util.List;

public class ComponentDefaults {
  @Nullable
  private final String myDefaultAssignee;

  @Nullable
  private final String myDefaultQA;

  @Nullable
  private final List<String> myDefaultCC;

  public ComponentDefaults(String defaultAssignee, String defaultQA, List<String> defaultCC) {
    myDefaultAssignee = defaultAssignee;
    myDefaultQA = defaultQA;
    myDefaultCC = defaultCC;
  }

  @Nullable
  public String getDefaultAssignee() {
    return myDefaultAssignee;
  }

  @Nullable
  public String getDefaultQA() {
    return myDefaultQA;
  }

  @Nullable
  public List<String> getDefaultCC() {
    return myDefaultCC;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ComponentDefaults that = (ComponentDefaults) o;

    if (myDefaultAssignee != null ? !myDefaultAssignee.equals(that.myDefaultAssignee) : that.myDefaultAssignee != null)
      return false;
    if (myDefaultCC != null ? !myDefaultCC.equals(that.myDefaultCC) : that.myDefaultCC != null)
      return false;
    if (myDefaultQA != null ? !myDefaultQA.equals(that.myDefaultQA) : that.myDefaultQA != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myDefaultAssignee != null ? myDefaultAssignee.hashCode() : 0;
    result = 31 * result + (myDefaultQA != null ? myDefaultQA.hashCode() : 0);
    result = 31 * result + (myDefaultCC != null ? myDefaultCC.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "CD{" + "a='" + myDefaultAssignee + '\'' + ", q='" + myDefaultQA +
      '\'' + ", cc=" + myDefaultCC + '}';
  }
}
