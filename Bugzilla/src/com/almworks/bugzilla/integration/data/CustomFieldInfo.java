package com.almworks.bugzilla.integration.data;

import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.List;

public class CustomFieldInfo {
  private final String myId;
  private final String myDisplayName;
  private final CustomFieldType myType;
  private final int myOrder;

  @Nullable
  private final Boolean myAvailableOnSubmit;

  @Nullable
  private final List<String> myOptions;

  public CustomFieldInfo(String id, String displayName, CustomFieldType type, int order,
    @Nullable Boolean availableOnSubmit, @Nullable List<String> options)
  {
    myId = id;
    myDisplayName = displayName;
    myType = type;
    myOrder = order;
    myAvailableOnSubmit = availableOnSubmit;
    myOptions = options;
  }

  public String getId() {
    return myId;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public CustomFieldType getType() {
    return myType;
  }

  public int getOrder() {
    return myOrder;
  }

  @Nullable
  public Boolean getAvailableOnSubmit() {
    return myAvailableOnSubmit;
  }

  @Nullable
  public List<String> getOptions() {
    return myOptions;
  }

  public String toString() {
    return myId + ":" + myDisplayName + ":" + myType + ":" + myAvailableOnSubmit + ":" + myOrder + ":" + myOptions;
  }

  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CustomFieldInfo that = (CustomFieldInfo) o;

    if (myOrder != that.myOrder)
      return false;
    if (myAvailableOnSubmit != null ? !myAvailableOnSubmit.equals(that.myAvailableOnSubmit) :
      that.myAvailableOnSubmit != null)
      return false;
    if (!myDisplayName.equals(that.myDisplayName))
      return false;
    if (!myId.equals(that.myId))
      return false;
    if (myOptions != null ? !myOptions.equals(that.myOptions) : that.myOptions != null)
      return false;
    if (myType != that.myType)
      return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myId.hashCode();
    result = 31 * result + myDisplayName.hashCode();
    result = 31 * result + myType.hashCode();
    result = 31 * result + myOrder;
    result = 31 * result + (myAvailableOnSubmit != null ? myAvailableOnSubmit.hashCode() : 0);
    result = 31 * result + (myOptions != null ? myOptions.hashCode() : 0);
    return result;
  }



  public static CustomFieldInfo merge(CustomFieldInfo a, CustomFieldInfo b) {
    if (a == null) return b;
    if (b == null) return a;
    if (!Util.equals(a.getId(), b.getId())) {
      Log.warn("custom field infos not mergeable: " + a + " " + b);
      return a;
    }
    boolean diff = false;
    String id = a.getId();
    String displayName;
    CustomFieldType type;
    int order;
    Boolean availableOnSubmit;
    List<String> options;

    displayName = a.getDisplayName();
    if (!Util.equals(displayName, b.getDisplayName())) {
      diff = true;
      String v = b.getDisplayName();
      // let's use the longest name
      displayName = Util.NN(displayName).length() > Util.NN(v).length() ? displayName : v;
    }

    type = a.getType();
    if (type != b.getType()) {
      diff = true;
      CustomFieldType v = b.getType();
      if (type == CustomFieldType.UNKNOWN) {
        type = v;
      } else if (v != CustomFieldType.UNKNOWN) {
        Log.warn("conflicting cf types: " + a + " " + b + ": used " + type);
      }
    }

    order = a.getOrder();
    // don't merge order

    availableOnSubmit = a.getAvailableOnSubmit();
    if (!Util.equals(availableOnSubmit, b.getAvailableOnSubmit())) {
      diff = true;
      if (availableOnSubmit == null) {
        availableOnSubmit = b.getAvailableOnSubmit();
      } if (b.getAvailableOnSubmit() != null) {
        Log.warn("conflicting cf aos: " + a + " " + b + ": used " + availableOnSubmit);
      }
    }

    options = a.getOptions();
    if (!Util.equals(options, b.getOptions())) {
      diff = true;
      if (options == null || options.isEmpty()) {
        options = b.getOptions();
      } else //noinspection ConstantConditions
        if (b.getOptions() != null && !b.getOptions().isEmpty()) {
        Log.warn("conflicting cf options: " + a + " " + b + ": used " + options);
      }
    }

    if (!diff) return a;
    return new CustomFieldInfo(id, displayName, type, order, availableOnSubmit, options);
  }
}
