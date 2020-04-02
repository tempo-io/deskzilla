package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

public class BugzillaProductInformation {
  private final String myProduct;

  private List<Pair<BugGroupData, Boolean>> myGroups;
  private boolean myInvalid;
  private boolean myDescriptionMayBePrivate;
  private Map<String, CustomFieldInfo> myCustomFieldInfo;
  private MultiMap<String, String> myCustomFieldDefaultValues;
  private Map<BugzillaAttribute, String> myDefaultValues;

  private int myTotalVotes;
  private int myUsedVotes;
  private int myVotesPerBug;

  private List<BugzillaUser> myUserList;
  private List<String> myInitialStatuses;

  @Nullable
  private Map<String, ComponentDefaults> myComponentDefaults;
  private CustomFieldDependencies myCustomFieldDependencies;


  public int getUsedVotes() {
    return myUsedVotes;
  }

  public void setUsedVotes(int usedVotes) {
    myUsedVotes = usedVotes;
  }

  public int getVotesPerBug() {
    return myVotesPerBug;
  }

  public void setVotesPerBug(int votesPerBug) {
    myVotesPerBug = votesPerBug;
  }


  public BugzillaProductInformation(String product) {
    myProduct = product;
  }

  public int getTotalVotes() {
    return myTotalVotes;
  }

  public void setTotalVotes(int totalVotes) {
    myTotalVotes = totalVotes;
  }

  public void setGroups(List<Pair<BugGroupData, Boolean>> groups) {
    myGroups = groups;
  }

  public void setInvalid() {
    myInvalid = true;
  }

  public List<Pair<BugGroupData, Boolean>> getGroups() {
    return myGroups;
  }

  public boolean isDescriptionMayBePrivate() {
    return myDescriptionMayBePrivate;
  }

  public void setDescriptionMayBePrivate(boolean descriptionMayBePrivate) {
    myDescriptionMayBePrivate = descriptionMayBePrivate;
  }

  public void setCustomFieldInfo(Map<String, CustomFieldInfo> cfinfo) {
    assert myCustomFieldInfo == null;
    myCustomFieldInfo = cfinfo;
  }

  public void setCustomFieldDefaultValues(MultiMap<String, String> values) {
    assert myCustomFieldDefaultValues == null;
    myCustomFieldDefaultValues = values;
  }

  public Map<String, CustomFieldInfo> getCustomFieldInfo() {
    return myCustomFieldInfo;
  }

  public MultiMap<String, String> getCustomFieldDefaultValues() {
    return myCustomFieldDefaultValues;
  }

  public void setDefaultValues(Map<BugzillaAttribute, String> defaultValues) {
    myDefaultValues = defaultValues;
  }

  public Map<BugzillaAttribute, String> getDefaultValues() {
    return myDefaultValues;
  }

  public void setUserList(List<BugzillaUser> userList) {
    myUserList = userList;
  }

  @NotNull
  public List<BugzillaUser> getUserList() {
    return myUserList != null ? myUserList : Collections.<BugzillaUser>emptyList();
  }

  public void setInitialStatuses(List<String> initalStatuses) {
    myInitialStatuses = initalStatuses == null ? null : Collections15.arrayList(initalStatuses);
  }

  public List<String> getInitialStatuses() {
    return myInitialStatuses;
  }

  public void setComponentDefaults(Map<String, ComponentDefaults> componentDefaults) {
    myComponentDefaults = componentDefaults == null ? null : Collections.unmodifiableMap(componentDefaults);
  }

  @Nullable
  public Map<String, ComponentDefaults> getComponentDefaults() {
    return myComponentDefaults;
  }

  public void setCustomFieldDependencies(CustomFieldDependencies dependencies) {
    myCustomFieldDependencies = dependencies;
  }

  public CustomFieldDependencies getCustomFieldDependencies() {
    return myCustomFieldDependencies;
  }
}