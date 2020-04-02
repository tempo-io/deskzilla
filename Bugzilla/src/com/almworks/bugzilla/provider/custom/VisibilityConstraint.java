package com.almworks.bugzilla.provider.custom;

import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.items.api.DBAttribute;
import org.jetbrains.annotations.*;

import java.util.List;

public class VisibilityConstraint {
  private final String myControllerFieldId;
  private final DBAttribute myControllerAttr;
  private final List<String> myControllerValueIds;
  private final LongList myControllerValueItems;

  public VisibilityConstraint(String controllerFieldId, DBAttribute controllerAttr, List<String> controllerValueIds,
    LongList controllerValueItems)
  {
    myControllerFieldId = controllerFieldId;
    myControllerValueIds = controllerValueIds;
    myControllerAttr = controllerAttr;
    myControllerValueItems = controllerValueItems;
  }

  @NotNull
  public String getControllerFieldId() {
    return myControllerFieldId;
  }

  @NotNull
  public List<String> getControllerValueIds() {
    return myControllerValueIds;
  }

  public boolean isAllowed(ItemHypercube cube) {
    for (LongListIterator i = myControllerValueItems.iterator(); i.hasNext(); ) {
      if (cube.allows(myControllerAttr, i.next())) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return myControllerFieldId + " in " + myControllerValueIds +
      " (" + myControllerAttr + " in " + myControllerValueItems + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    VisibilityConstraint that = (VisibilityConstraint) o;

    if (myControllerAttr != null ? !myControllerAttr.equals(that.myControllerAttr) : that.myControllerAttr != null)
      return false;
    if (myControllerFieldId != null ? !myControllerFieldId.equals(that.myControllerFieldId) :
      that.myControllerFieldId != null)
      return false;
    if (myControllerValueIds != null ? !myControllerValueIds.equals(that.myControllerValueIds) :
      that.myControllerValueIds != null)
      return false;
    if (myControllerValueItems != null ? !myControllerValueItems.equals(that.myControllerValueItems) :
      that.myControllerValueItems != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myControllerFieldId != null ? myControllerFieldId.hashCode() : 0;
    result = 31 * result + (myControllerAttr != null ? myControllerAttr.hashCode() : 0);
    result = 31 * result + (myControllerValueIds != null ? myControllerValueIds.hashCode() : 0);
    result = 31 * result + (myControllerValueItems != null ? myControllerValueItems.hashCode() : 0);
    return result;
  }
}
