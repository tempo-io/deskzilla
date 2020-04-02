package com.almworks.bugzilla.provider.meta.groups;

import com.almworks.api.application.ExportValueType;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.util.Pair;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderable;
import com.almworks.util.components.renderer.CellState;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

public class BugGroupInfo implements CanvasRenderable, Comparable<BugGroupInfo> {
  public static final BugGroupInfo GROUPS_UNKNOWN = new BugGroupInfo().freeze();

  public static final BaseModelKey.Export<? super BugGroupInfo> EXPORT =
    new BaseModelKey.Export.Simple<BugGroupInfo>(ExportValueType.STRING) {
      protected String convertToString(BugGroupInfo value, NumberFormat numberFormat, DateFormat dateFormat) {
        return value == null ? "" : value.toDisplayString();
      }
    };


  // id => (description, on/off)
  private final Map<String, GroupInfo> myGroups = Collections15.linkedHashMap();
  private boolean myReadOnly;
  private String myDisplayStringCache;

  @Nullable
  private Map<String, Boolean> myChanges;


  public BugGroupInfo() {
  }

  public BugGroupInfo(BugGroupInfo copyFrom) {
    if (copyFrom != null)
      myGroups.putAll(copyFrom.myGroups);
    // changes are not copied
  }

  public void addGroup(String id, long groupItem, String description, boolean state) {
    if (myReadOnly) {
      assert false : this;
      return;
    }
    myGroups.put(id, new GroupInfo(groupItem, description, state));
  }

  public void updateGroup(String id, boolean state) {
    if (myReadOnly) {
      assert false : this;
      return;
    }
    GroupInfo gi = myGroups.get(id);
    if (gi != null) {
      myGroups.put(id, new GroupInfo(gi.item, gi.description, state));
    }
    Map<String, Boolean> map = myChanges;
    if (map == null)
      myChanges = map = Collections15.linkedHashMap();
    map.put(id, state);
  }


  public BugGroupInfo freeze() {
    myReadOnly = true;
    return this;
  }

  public String toString() {
    if (this == GROUPS_UNKNOWN)
      return "unknown";
    StringBuilder r = new StringBuilder();
    for (GroupInfo gi : myGroups.values()) {
      if (r.length() > 0)
        r.append(' ');
      r.append(gi.description).append(':').append(Boolean.TRUE.equals(gi.state) ? 'Y' : 'N');
    }
    return r.toString();
  }

  public String toDisplayString() {
//    if (this == GROUPS_UNKNOWN)
//      return "?";
    if (myReadOnly) {
      String displayString = myDisplayStringCache;
      if (displayString != null)
        return displayString;
    }
    StringBuilder buffer = null;
    for (GroupInfo gi : myGroups.values()) {
      if (gi.state) {
        if (buffer == null)
          buffer = new StringBuilder();
        else
          buffer.append(", ");
        buffer.append(gi.description);
      }
    }
    String result = buffer == null ? "" : buffer.toString();
    myDisplayStringCache = result;
    return result;
  }

  /**
   * @return null if there's no mapping
   */
  public Boolean getState(String id) {
    GroupInfo gi = myGroups.get(id);
    return gi == null ? null : gi.state;
  }

  public void renderOn(Canvas canvas, CellState state) {
    String s = toDisplayString();
    if (s.length() > 0)
      canvas.appendText(s);
  }

  public int compareTo(BugGroupInfo that) {
    String d1 = this.toDisplayString();
    String d2 = that.toDisplayString();
    return d1.compareTo(d2);
  }

  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    BugGroupInfo that = (BugGroupInfo) o;

    if (!myGroups.equals(that.myGroups))
      return false;

    return true;
  }

  public int hashCode() {
    return myGroups.hashCode();
  }

  public Map<String, Pair<String, Boolean>> getAll() {
    Map<String, Pair<String, Boolean>> map = Collections15.linkedHashMap();
    for (Map.Entry<String, GroupInfo> entry : myGroups.entrySet()) {
      GroupInfo gi = entry.getValue();
      map.put(entry.getKey(), Pair.create(gi.description, gi.state));
    }
    return map;
  }

  public Set<String> getKeys() {
    return Collections15.linkedHashSet(myGroups.keySet());
  }

  public void applyChangesFrom(BugGroupInfo groups) {
    if (groups == null)
      return;
    Map<String, Boolean> changes = groups.myChanges;
    if (changes == null)
      return;
    for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
      updateGroup(entry.getKey(), entry.getValue());
    }
  }

  @Nullable
  public Map<Long, Boolean> getAllForDatabase() {
    if (myGroups.size() == 0)
      return null;
    LinkedHashMap<Long, Boolean> result = Collections15.linkedHashMap();
    for (GroupInfo gi : myGroups.values()) {
      result.put(gi.item, gi.state);
    }
    return result;
  }


  private static class GroupInfo {
    private final long item;
    private final String description;
    private final boolean state;

    public GroupInfo(long item, String description, boolean state) {
      this.item = item;
      this.description = description;
      this.state = state;
    }

    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      GroupInfo groupInfo = (GroupInfo) o;

      if (state != groupInfo.state)
        return false;
      if (item > 0 ? item != groupInfo.item : groupInfo.item > 0)
        return false;
      if (description != null ? !description.equals(groupInfo.description) : groupInfo.description != null)
        return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (int)(item ^ (item >>> 32));
      result = 31 * result + (description != null ? description.hashCode() : 0);
      result = 31 * result + (state ? 1 : 0);
      return result;
    }
  }
}
