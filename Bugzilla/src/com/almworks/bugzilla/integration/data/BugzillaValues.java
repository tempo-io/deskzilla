package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugzillaValues {
  private static final String[] EMPTY = {};

  /**
   * Holds single-string values.
   */
  private final Map<BugzillaAttribute, String> myScalars = Collections15.hashMap();

  /**
   * Holds multiple-string values in order of appearance.
   * Known tuples: CC, DependsOn, Blocked
   * <p/>
   * Note that keywords are given in one value, comma-separated
   */
  private final Map<BugzillaAttribute, List<String>> myTuples = Collections15.hashMap();

  private boolean myReadOnly = false;

  /**
   * @return non-null
   */
  public List<String> getTupleValues(BugzillaAttribute attribute) {
    List<String> tuple = myTuples.get(attribute);
    if (tuple != null)
      return tuple;
    String singleValue = myScalars.get(attribute);
    if (singleValue != null)
      return Collections.singletonList(singleValue);
    return Collections.emptyList();
  }

  public String getScalarValue(BugzillaAttribute attribute, String defaultValue) {
    List<String> values = myTuples.get(attribute);
    if (values != null) {
      if (values.size() > 1) {
        Log.warn("attribute " + attribute + " has multiple values in " + this);
        // Alias may have several values since some Bugzilla version
//        assert false : this + ": " + attribute;
        return values.get(0);
      } else if (values.size() == 1) {
        Log.debug("weird: single value in a tuple");
        return values.get(0);
      } else {
        Log.debug("weird: tuple without values");
      }
    }
    if (myScalars.containsKey(attribute))
      return myScalars.get(attribute);
    else
      return defaultValue;
  }

  public String getMandatoryScalarValue(BugzillaAttribute attribute) {
    String value = getScalarValue(attribute, null);
    assert value != null : this + "." + attribute;
    return value;
  }

  public boolean contains(BugzillaAttribute attribute) {
    return myScalars.containsKey(attribute) || myTuples.containsKey(attribute);
  }

  public void put(BugzillaAttribute attribute, String value) {
    checkWriteAccess();
    // if there are several values for the same attribute, move it to List container
    // while there is only one value, keep it in single container
    if (myTuples.containsKey(attribute)) {
      myTuples.get(attribute).add(value);
    } else if (myScalars.containsKey(attribute)) {
      String firstValue = myScalars.remove(attribute);
      List<String> list = Collections15.<String>arrayList();
      myTuples.put(attribute, list);
      list.add(firstValue);
      list.add(value);
    } else {
      myScalars.put(attribute, value);
    }
  }


  public void putAll(BugzillaAttribute attribute, String[] tupleValue) {
    for (int i = 0; i < tupleValue.length; i++)
      put(attribute, tupleValue[i]);
  }

  public void putAll(BugzillaAttribute attribute, Collection<String> tupleValue) {
    for(final String s : tupleValue) {
      put(attribute, s);
    }
  }

  public void setReadOnly() {
    myReadOnly = true;
  }

  private void checkWriteAccess() {
    if (myReadOnly)
      throw new IllegalArgumentException("values are read-only");
  }


  public Map<BugzillaAttribute, String> getScalars() {
    return Collections.unmodifiableMap(myScalars);
  }

  public void clear(BugzillaAttribute attribute) {
    checkWriteAccess();
    myTuples.remove(attribute);
    myScalars.remove(attribute);
  }

  public void copy(BugzillaValues values) {
    myScalars.clear();
    myScalars.putAll(values.myScalars);
    myTuples.clear();
    myTuples.putAll(values.myTuples);
  }

  public void reput(BugzillaAttribute attribute, String value) {
    clear(attribute);
    put(attribute, value);
  }

  public Map<BugzillaAttribute, List<String>> getTuples() {
    Map<BugzillaAttribute, List<String>> result = Collections15.hashMap();
    for (Iterator<Map.Entry<BugzillaAttribute, List<String>>> ii = myTuples.entrySet().iterator(); ii.hasNext();) {
      Map.Entry<BugzillaAttribute, List<String>> entry = ii.next();
      result.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  public Object getValue(BugzillaAttribute attribute) {
    Object r = myScalars.get(attribute);
    if (r != null)
      return r;
    List<String> list = myTuples.get(attribute);
    if (list == null || list.isEmpty())
      return null;
    if (list.size() == 1)
      return list.get(0);
    return list;
  }

  public int getSize() {
    return myScalars.size() + myTuples.size();
  }
}
