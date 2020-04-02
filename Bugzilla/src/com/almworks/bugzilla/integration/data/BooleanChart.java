package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;

/**
 * @author Vasya
 */
public class BooleanChart {
  private final List<Group> myGroups = Collections15.arrayList();

  public void addGroup(@Nullable Group group) {
    if (group != null)
      myGroups.add(group);
  }

  public boolean isEmpty() {
    if (myGroups.isEmpty())
      return true;
    for (Group group : myGroups) {
      if (group.getElements().isEmpty())
        return true;
    }
    return false;
  }

  public List<Group> getGroups() {
    return Collections.unmodifiableList(myGroups);
  }

  public static class Group {
    private final List<Element> myElements = Collections15.arrayList();

    public void addElement(@Nullable Element element) {
      if (element == null)
        return;
      myElements.add(element);
    }

    public List<Element> getElements() {
      return Collections.unmodifiableList(myElements);
    }

    public boolean isEmpty() {
      return myElements.isEmpty();
    }
  }

  public static class Element {
    private final BugzillaAttribute myField;
    private final BooleanChartElementType myType;
    private final String myValue;
    private final String mySpecial;

    private Element(BugzillaAttribute field, String special, BooleanChartElementType type, String value) {
      assert type != null;
      assert value != null;

      mySpecial = special;
      myField = field;
      myType = type;
      myValue = value;
    }

    public BugzillaAttribute getField() {
      return myField;
    }

    public String getSpecial() {
      return mySpecial;
    }

    public BooleanChartElementType getType() {
      return myType;
    }

    public String getValue() {
      return myValue;
    }
  }

  public static Element createElement(BugzillaAttribute field, BooleanChartElementType type, String value) {
    return new Element(field, null, type, value);
  }

  public static Element createElement(String special, BooleanChartElementType type, String value) {
    return new Element(null, special, type, value);
  }

}
