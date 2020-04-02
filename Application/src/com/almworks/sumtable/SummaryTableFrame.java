package com.almworks.sumtable;

import com.almworks.api.application.*;
import com.almworks.api.application.tree.*;
import com.almworks.api.application.util.sources.ItemViewAdapter;
import com.almworks.api.gui.*;
import com.almworks.api.install.Setup;
import com.almworks.items.api.DBFilter;
import com.almworks.items.api.DP;
import com.almworks.util.Constant;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.ToolbarBuilder;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.exec.Context;
import com.almworks.util.ui.UIComponentWrapper;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

public class SummaryTableFrame implements SummaryTableQueryExecutor {
  private WindowController myController = null;

  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private final SummaryTableMainPanel myMainPanel;

  private final FrameBuilder myBuilder;
  private final SummaryTableConfiguration myTableConfiguration;
  private final GenericNode myNode;
  private final Detach myOnWindowClosed;
  private final RowColumnConfig myRowColumnConfig;

  private final DetachComposite myLifespan = new DetachComposite();

  public SummaryTableFrame(
    SummaryTableConfiguration tableConfig, GenericNode node,
    String windowId, Detach onWindowClosed)
  {
    myTableConfiguration = tableConfig;
    myNode = node;
    myOnWindowClosed = onWindowClosed;
    WindowManager windowManager = Context.require(WindowManager.ROLE);
    myBuilder = windowManager.createFrame(windowId);
    myMainPanel = new SummaryTableMainPanel(myBuilder.getConfiguration(), tableConfig, true, this);
    myBuilder.setTitle(node.getName() + " - Summary Table - " + Setup.getProductName());
    myRowColumnConfig = new RowColumnConfig(myTableConfiguration);

    myWholePanel.add(myMainPanel.getComponent(), BorderLayout.CENTER);
    myWholePanel.add(createToolbar(), BorderLayout.NORTH);

    myBuilder.setContent(new UIComponentWrapper.Simple(myWholePanel));
  }

  private AToolbar createToolbar() {
    ToolbarBuilder builder = ToolbarBuilder.buttonsWithText();
    builder.addAction(new ConfigureSummaryTableAction(myMainPanel));
    builder.addAction(new ExportToCSVAction());
    builder.addSeparator();
    myRowColumnConfig.buildToolbar(builder);
    AToolbar toolbar = builder.createHorizontalToolbar();
    Aero.addSouthBorder(toolbar);
    return toolbar;
  }

  public void show() {
    if (myController == null) {
      if (myOnWindowClosed != null) {
        myLifespan.add(myOnWindowClosed);
      }
      myMainPanel.attach(myLifespan, myNode);
      myRowColumnConfig.attach(myLifespan, myNode.getQueryResult());
      myBuilder.showWindow(myLifespan);
      myController = myBuilder.getWindowContainer().getActor(WindowController.ROLE);
    } else {
      if (!myController.isVisible())
        myController.show();
      myController.toFront();
    }
  }

  public void runQuery(QueryResult queryResult, STFilter counter, STFilter column, STFilter row, Integer count, boolean newTab) {
    executeQuery(myNode, queryResult, counter, column, row, count, newTab);
  }

  public static void executeQuery(GenericNode node, QueryResult queryResult, STFilter counter, STFilter column,
    STFilter row, Integer count, boolean newTab)
  {
    ExplorerComponent explorer = Context.require(ExplorerComponent.ROLE);
    DBFilter parentView = QueryUtil.maybeGetHintedView(node, queryResult);
    if (parentView == null) {
      assert false : node;
      return;
    }
    DBFilter view = counter.filter(column.filter(row.filter(parentView)));
    ItemSource source = ItemViewAdapter.create(view, new Constant<Integer>(count));
    TabKey key = newTab ? new SimpleTabKey() : new MyKey(node.getNodeId());
    ItemCollectionContext contextInfo = ItemCollectionContext.createSummary(
      buildConstraint(column, row, counter), node, key, Collections.singletonList(node));
    explorer.showItemsInTab(source, contextInfo, false);
  }

  private static String buildConstraint(STFilter ... filters) {
    String separator = "";
    StringBuilder builder = new StringBuilder();
    for (STFilter f : filters) {
      BoolExpr<DP> filter = f.getFilter();
      if (filter == null || filter.equals(BoolExpr.<Object>TRUE()))
        continue;
      builder.append(separator);
      builder.append(f.getName());
      separator = ", ";
    }
    return builder.toString();
  }

  public static SummaryTableFrame create(SummaryTableConfiguration configuration, GenericNode node, String windowId,
    Detach onWindowClosed)
  {
    return new SummaryTableFrame(configuration, node, windowId, onWindowClosed);
  }

  private static class MyKey implements TabKey {
    private final String myNodeId;

    public MyKey(String nodeId) {
      myNodeId = nodeId;
    }

    public boolean isReplaceTab(TabKey tabKey) {
      if (!(tabKey instanceof MyKey))
        return false;
      return myNodeId.equals(((MyKey) tabKey).myNodeId);
    }


    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      MyKey myKey = (MyKey) o;

      if (myNodeId != null ? !myNodeId.equals(myKey.myNodeId) : myKey.myNodeId != null)
        return false;

      return true;
    }

    public int hashCode() {
      return (myNodeId != null ? myNodeId.hashCode() : 0);
    }
  }
}

