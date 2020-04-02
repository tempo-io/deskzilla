package com.almworks.util.components;

import javax.swing.event.*;
import javax.swing.tree.ExpandVetoException;

public class TreeExpansionAdapter implements TreeExpansionListener, TreeWillExpandListener {
  public static final TreeExpansionAdapter STUB = new TreeExpansionAdapter();

  public void treeExpanded(TreeExpansionEvent event) {
  }

  public void treeCollapsed(TreeExpansionEvent event) {
  }

  public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
  }

  public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
  }
}
