package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * This class holds the information which is visible in the Bugzilla's
 * query page.
 *
 * @author sereda
 */
public class BugzillaLists {
  private final Map<BugzillaAttribute, List<String>> myStringLists = Collections15.hashMap();
  private final Set<BugzillaAttribute> myMissingLists = Collections15.hashSet();
  // attribute -> map<product -> list of options>
  private final Map<BugzillaAttribute, Map<String, List<String>>> myProductDependencies = Collections15.hashMap();

  // field id => field displayable name
  private Map<String, String> myCustomFieldNames;

  private boolean myFixed = false;
  private boolean myDependenciesPresent = false;

  public void fix() {
    assert !myFixed;
    myFixed = true;
/*
    for (Iterator<BugzillaAttribute> ii = myStringLists.keySet().iterator(); ii.hasNext();) {
      BugzillaAttribute key = ii.next();
      myStringLists.put(key, Collections.unmodifiableList(myStringLists.access(key)));
    }

*/
  }

  public List<String> getStringList(BugzillaAttribute key) {
    List<String> result = myStringLists.get(key);
    if (result == null) {
      if (myFixed) {
        return Collections15.emptyList();
      } else {
        result = Collections15.arrayList();
        myStringLists.put(key, result);
      }
    }
    return result;
  }

  public Map<String, List<String>> getProductDependencyMap(BugzillaAttribute key) {
    Map<String, List<String>> map = myProductDependencies.get(key);
    if (map == null) {
      if (myFixed) {
        return Collections15.emptyMap();
      } else {
        map = Collections15.hashMap();
        myProductDependencies.put(key, map);
      }
    }
    return map;
  }

  public Map<BugzillaAttribute, List<String>> getLists() {
    return Collections.unmodifiableMap(myStringLists);
  }

  public synchronized void setListIsMissing(BugzillaAttribute attribute) {
    assert !myStringLists.containsKey(attribute) : attribute;
    myMissingLists.add(attribute);
  }

  public synchronized boolean isListMissing(BugzillaAttribute attribute) {
    return myMissingLists.contains(attribute);
  }

  public void setDependenciesPresent(boolean dependenciesPresent) {
    myDependenciesPresent = dependenciesPresent;
  }

  public boolean isDependenciesPresent() {
    return myDependenciesPresent;
  }

  public void addCustomFieldName(String fieldId, String displayableName) {
    if (myCustomFieldNames == null)
      myCustomFieldNames = Collections15.linkedHashMap();
    myCustomFieldNames.put(fieldId, displayableName);
  }

  public Map<String, String> getCustomFieldNames() {
    return myCustomFieldNames;
  }
}
