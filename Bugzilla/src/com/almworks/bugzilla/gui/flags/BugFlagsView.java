package com.almworks.bugzilla.gui.flags;

import com.almworks.bugzilla.provider.datalink.flags2.Flag;
import com.almworks.bugzilla.provider.datalink.flags2.FlagVersion;
import com.almworks.util.advmodel.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.*;
import com.almworks.util.components.layout.WidthDrivenComponent;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.tables.SortingTableHeaderController;
import com.almworks.util.config.Configuration;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.ui.DocumentFormAugmentor;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

public class BugFlagsView implements WidthDrivenComponent, Highlightable {
  private final ASortedTable<FlagVersion> myTable = new ASortedTable<FlagVersion>();
  private static final String COLUMNS_CONFIG = "columns";
  private final ListModelHolder<FlagVersion> myRows = ListModelHolder.create();
  private final Lifecycle myLife = new Lifecycle(false);

  public BugFlagsView() {
    setupVisuals();
  }

  private void setupVisuals() {
    myTable.setStriped(true);
    myTable.setGridHidden();
    myTable.setScrollableMode(false);
    if(Aqua.isAqua()) {
      final JTable jTable = (JTable) myTable.getSwingComponent();
      DocumentFormAugmentor.DO_NOT_AUGMENT.putClientValue(jTable.getTableHeader(), true);
    }
    JComponent details = myTable.toComponent();
    if (details instanceof JScrollPane) {
      details.setBorder(Aqua.isAqua() ? Aqua.MAC_LIGHT_BORDER_NORTH : AwtUtil.EMPTY_BORDER);
    }
  }

  public void init(Configuration config) {
    myLife.cycle();
    setupRows(myLife.lifespan());
    setupColumns(myLife.lifespan(), config);
  }

  private void setupRows(Lifespan life) {
    life.add(myTable.setCollectionModel(myRows));
  }

  private void setupColumns(Lifespan life, Configuration config) {
    boolean firstTime = !config.isSet(COLUMNS_CONFIG);
    AListModel<? extends TableColumnAccessor<FlagVersion, ?>> columns = BugFlagsColumns.all();
    SortingTableHeaderController header = myTable.getHeaderController();
    header.setUserFullColumnsModel(life, columns, false);
    myTable.setColumnModel(header.getUserFilteredColumnModel());
    Configuration columnsConfig = config.getOrCreateSubset(COLUMNS_CONFIG);
    ColumnsConfiguration.install(life, columnsConfig, header);
    firstTime = ensureAllColumnsPresent(firstTime, header);
    if (firstTime) {
      myTable.forcePreferredColumnWidths();
    }
  }

  /**
   * Sometimes, config may get broken and a column may be missing from it. Since we know that all columns must be present, we act as if there was no config at all. 
   */
  private boolean ensureAllColumnsPresent(boolean firstTime, SortingTableHeaderController header) {
    SubsetModel subsetModel = header.getUserColumnsSubsetModel();
    if (subsetModel.getComplementSet().getSize() > 0) {
      firstTime = true;
      subsetModel.removeAll(Condition.<Object>always());
      subsetModel.addFromComplementSet(subsetModel.getComplementSet().toList());
    }
    return firstTime;
  }

  @Override
  public int getPreferredWidth() {
    return myTable.getPreferredSize().width;
  }

  @Override
  public int getPreferredHeight(int width) {
    JComponent table = myTable.getSwingComponent();
    JComponent scrollpane = myTable.toComponent();
    Insets scrollinsets = AwtUtil.uniteInsetsFromTo((JComponent) table.getParent(), scrollpane);
    int tableHeight = table.getPreferredSize().height;
    if (table instanceof JTable) {
      tableHeight += ((JTable) table).getTableHeader().getPreferredSize().height;
    }
    return tableHeight + scrollinsets.top + scrollinsets.bottom;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myTable.toComponent();
  }

  @Override
  public boolean isVisibleComponent() {
    return getComponent().isVisible();
  }

  public void setRows(Lifespan life, List<FlagVersion> rowsList) {
    AListModel<FlagVersion> rows = FixedListModel.create(rowsList);
    AListModel<FlagVersion> sortedRows = SortedListDecorator.create(life, rows, Flag.ORDER);
    life.add(myRows.setModel(sortedRows));
  }

  @Override
  public void setHighlightPattern(Pattern pattern) {
    myTable.setHighlightPattern(pattern);
  }
}
