package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.ModelKey;
import com.almworks.util.collections.Comparing;
import com.almworks.util.collections.Containers;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.models.*;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author dyoma
 */
public class ValueColumn<T> implements TableColumnAccessor<LoadedItem, T> {
  private final ModelKey<? extends T> myKey;
  private final Comparator<LoadedItem> myComparator;
  private final CollectionRenderer<LoadedItem> myRenderer;
  private final int myCharCount;
  private final String myHeaderText;

  public ValueColumn(ModelKey<? extends T> key, @NotNull Comparator<LoadedItem> defaultOrder,
    @Nullable final Comparator<T> valueComparator, int charCount, @Nullable ModelKey<Boolean> dummyFlag, String headerText)
  {
    myKey = key;
    myRenderer = Renderers.convertingRenderer(
      new DummyShadingRenderer(Renderers.createRenderer(myKey.getRenderer()), dummyFlag),
      LoadedItem.GET_VALUES);
    Comparator<LoadedItem> localComparator = valueComparator != null ? new Comparator<LoadedItem>() {
      public int compare(LoadedItem o1, LoadedItem o2) {
        T v1 = getValue(o1);
        T v2 = getValue(o2);
        return Comparing.compare(valueComparator, v1, v2);
      }
    } : null;
    myComparator = Containers.twoLevelComparator(localComparator, defaultOrder);
    myCharCount = charCount;
    myHeaderText = headerText;
  }

  public T getValue(LoadedItem item) {
    PropertyMap values = item.getValues();
    return myKey.getValue(values);
  }

  public String getName() {
    return myKey.getDisplayableName();
  }

  public String getId() {
    return myKey.getName();
  }

  public String getColumnHeaderText() {
    return myHeaderText == null ? myKey.getName() : myHeaderText;
  }

  @Override
  public String toString() {
    return myHeaderText;
  }

  public CollectionRenderer<LoadedItem> getDataRenderer() {
    return myRenderer;
  }

  @Nullable
  public CollectionEditor<LoadedItem> getDataEditor() {
    return null;
  }

  public Comparator<LoadedItem> getComparator() {
    return myComparator;
  }

  public int getPreferredWidth(JTable table, ATableModel<LoadedItem> tableModel, ColumnAccessor<LoadedItem> renderingAccessor,
    int columnIndex) {
    int pref = -1;
    ColumnSizePolicy sizePolicy = getSizePolicy();
    if (sizePolicy != null) {
      pref = sizePolicy.getPreferredWidth(table, tableModel, renderingAccessor, columnIndex);
    }
    if (pref <= 0 && myCharCount > 0) {
      pref = table.getFontMetrics(table.getFont()).charWidth('m') * myCharCount;
    }
    return pref;
  }

  public ColumnSizePolicy getSizePolicy() {
    return myKey.getRendererSizePolicy();
  }

  public String getHeaderTooltip() {
    return myKey.getDisplayableName();
  }

  public boolean isOrderChangeAllowed() {
    return true;
  }

  public boolean isSortingAllowed() {
    return true;
  }

  public ColumnTooltipProvider<LoadedItem> getTooltipProvider() {
    return null;
  }

  public static <T extends Comparable> ValueColumn<T> createComparable(ModelKey<? extends T> key,
    Comparator<LoadedItem> defaultOrder, int charCount, @Nullable ModelKey<Boolean> dummyFlag, String headerText)
  {
    return new ValueColumn<T>(key, defaultOrder, Containers.<T>comparablesComparator(), charCount, dummyFlag,
      headerText);
  }

  public <T> T getHint(@NotNull TypedKey<T> key) {
    return null;
  }
}
