package com.almworks.api.application;

import com.almworks.api.application.tree.*;
import com.almworks.api.engine.Connection;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.ui.UIComponentWrapper;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;

/**
 * @author : Dyoma
 */
public interface ExplorerComponent {
  Role<ExplorerComponent> ROLE = Role.role("ExplorerComponent");

  @ThreadAWT
  SearchResult showItemsInTab(@NotNull ItemSource source, ItemCollectionContext contextInfo, boolean focusToTable);

  void showItemInTab(@NotNull ItemWrapper item);

  void showItemInWindow(@NotNull ItemWrapper item);

  void showComponent(@NotNull UIComponentWrapper component, @NotNull String name);

  void createDefaultQueries(Connection connection, Runnable whenCreated);

  void expandConnectionNode(Connection connection, boolean expandAll);

  @ThreadAWT
  Map<DBIdentifiedObject, TagNode> getTags();

  @Nullable
  RootNode getRootNode();

  void setHighlightedNodes(TypedKey highlightKey, Collection<? extends GenericNode> nodes, Color color, Icon icon, String caption);

  void clearHighlightedNodes(TypedKey highlightKey);

  

  /**
   * Used to attach to data in main panel from dialogs.
   */
  @NotNull
  JComponent getGlobalContextComponent();

  ScalarModel<Boolean> isNavigationTreeReady();

  void whenReady(ThreadGate gate, Runnable runnable);

  void selectNavigationNode(GenericNode node);

  ItemsCollectionController createLoader(ItemCollectorWidget widget, ItemSource source);
}
