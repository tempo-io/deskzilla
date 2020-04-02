package com.almworks.api.application;

import com.almworks.api.engine.Connection;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.ui.actions.DataRole;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public abstract class ItemsTreeLayout implements CanvasRenderable {
  public static final String NO_HIERARCHY = "No hierarchy";
  public static final DataRole<ItemsTreeLayout> DATA_ROLE = DataRole.createRole(ItemsTreeLayout.class);
  public static final ItemsTreeLayout NONE =
    new ItemsTreeLayout(NO_HIERARCHY, TreeStructure.FlatTree.<LoadedItem, LoadedItem>instance(), "") {
      public boolean isApplicableTo(Connection connection) {
        return true;
      }
    };
  private final String myDisplayName;
  private final TreeStructure<LoadedItem, ?, TreeModelBridge<LoadedItem>> myTreeStructure;
  private final String myId;

  public ItemsTreeLayout(String displayName, TreeStructure<LoadedItem, ?, TreeModelBridge<LoadedItem>> treeStructure,
    String id) {
    myDisplayName = displayName;
    myTreeStructure = treeStructure;
    myId = id;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public TreeStructure<LoadedItem,?, TreeModelBridge<LoadedItem>> getTreeStructure() {
    return myTreeStructure;
  }

  public void renderOn(Canvas canvas, CellState state) {
    canvas.appendText(getDisplayName());
  }

  public String toString() {
    return "ATL[" + getDisplayName() +"]";
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public abstract boolean isApplicableTo(Connection connection);
}
