package com.almworks.util.models;

import com.almworks.util.collections.*;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.Renderers;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author dyoma
 */
public class TableColumnBuilder<E, V> {
  private String myId;
  private String myName;
  private String myHeaderText;
  private CollectionRenderer<E> myRenderer;
  private CollectionRenderer<? super V> myValueRenderer;
  private Comparator<E> myComparator;
  private ColumnSizePolicy mySizePolicy = ColumnSizePolicy.FREE;
  private String myTooltip;
  private boolean myReorderable = true;
  private Convertor<? super E, V> myConvertor;
  private Comparator<? super V> myValueComparator;
  private final UserDataHolder myUserData = new UserDataHolder();
  private Comparator<E> mySecondLevelComparator;
  private CollectionEditor<E> myEditor;
  private ColumnTooltipProvider<E> myTooltipProvider;

  public TableColumnBuilder<E, V> setId(String id) {
    assert id != null;
    myId = id;
    return this;
  }

  public TableColumnBuilder<E, V> setName(String name) {
    myName = name;
    return this;
  }

  public TableColumnBuilder<E, V> setHeaderText(String headerText) {
    myHeaderText = headerText;
    return this;
  }

  public TableColumnBuilder<E, V> setCanvasRenderer(CanvasRenderer<E> renderer) {
    assert renderer != null;
    myRenderer = Renderers.createRenderer(renderer);
    return this;
  }

  public TableColumnBuilder<E, V> setValueCanvasRenderer(CanvasRenderer<? super V> renderer) {
    assert renderer != null;
    myValueRenderer = Renderers.createRenderer(renderer);
    return this;
  }

  public TableColumnBuilder<E, V> setRenderer(CollectionRenderer<E> renderer) {
    assert renderer != null;
    myRenderer = renderer;
    return this;
  }

  public TableColumnBuilder<E, V> setEditor(CollectionEditor<E> editor) {
    myEditor = editor;
    return this;
  }

  public TableColumnBuilder<E, V> setComparator(Comparator<E> comparator) {
    myComparator = comparator;
    return this;
  }

  public void setValueComparator(Comparator<? super V> comparator) {
    myValueComparator = comparator;
  }

  public TableColumnBuilder<E, V> setSizePolicy(ColumnSizePolicy sizePolicy) {
    assert sizePolicy != null;
    mySizePolicy = sizePolicy;
    return this;
  }

  public void setTooltip(String tooltip) {
    myTooltip = tooltip;
  }

  public void setTooltipProvider(ColumnTooltipProvider<E> tooltipProvider) {
    myTooltipProvider = tooltipProvider;
  }

  public TableColumnBuilder<E, V> setConvertor(Convertor<? super E, V> convertor) {
    assert convertor != null;
    myConvertor = convertor;
    return this;
  }

  public TableColumnBuilder<E, V> setReorderable(boolean reorderable) {
    myReorderable = reorderable;
    return this;
  }

  public <T> T getUserData(TypedKey<T> key) {
    return myUserData.getUserData(key);
  }

  public <T> void putUserData(TypedKey<T> key, T data) {
    myUserData.putUserDate(key, data);
  }

  public static <T> TableColumnBuilder<T, T> identity() {
    TableColumnBuilder<T, T> builder = create();
    builder.setConvertor(Convertors.<T>identity());
    return builder;
  }

  public static <E, V> TableColumnBuilder<E, V> create() {
    return new TableColumnBuilder<E, V>();
  }

  public static <E, V> TableColumnBuilder<E, V> create(String id, String name) {
    TableColumnBuilder<E, V> builder = new TableColumnBuilder<E, V>();
    builder.setId(id);
    builder.setName(name);
    return builder;
  }

  public TableColumnAccessor<E, V> createColumn() {
    assert myId != null;
    assert myRenderer != null || myValueRenderer != null;
    assert mySizePolicy != null;
    assert myConvertor != null;
    if (myRenderer == null)
      myRenderer = Renderers.convertingRenderer(myValueRenderer, myConvertor);
    if (myComparator == null && myValueComparator != null)
      myComparator = Containers.convertingComparator(myConvertor.toReadAccessor(), myValueComparator);
    Comparator<E> comparator;
    if (mySecondLevelComparator != null && myComparator != null)
      comparator = Containers.twoLevelComparator(myComparator, mySecondLevelComparator);
    else
      comparator = myComparator;
    return new TableColumnImpl<E, V>(myId, getName(), getHeaderText(), myRenderer, myEditor, comparator, mySizePolicy,
      getTooltip(), myTooltipProvider, myReorderable, myConvertor, myUserData);
  }

  public String getName() {
    return myName != null ? myName : myId;
  }

  public String getHeaderText() {
    return myHeaderText != null ? myHeaderText : getName();
  }

  public String getTooltip() {
    return myTooltip != null ? myTooltip : getName();
  }

  public static <T> TableColumnBuilder<T, T> simple() {
    TableColumnBuilder<T, T> builder = new TableColumnBuilder<T, T>();
    builder.setConvertor(Convertors.<T>identity());
    return builder;
  }

  public void setSecondLevelComparator(Comparator<E> comparator) {
    mySecondLevelComparator = comparator;
  }

  public static class TableColumnImpl<E, V> implements TableColumnAccessor<E, V> {
    private final UserDataHolder myUserData;
    private final String myId;
    private final String myName;
    private final String myHeaderText;
    private final CollectionRenderer<E> myRenderer;
    private final CollectionEditor<E> myEditor;
    private final Comparator<E> myComparator;
    private final ColumnSizePolicy myPrefSizePolicy;
    private final String myTooltip;
    private final boolean myReorderable;
    private Convertor<? super E, V> myConvertor;
    private ColumnTooltipProvider<E> myTooltipProvider;

    private TableColumnImpl(String id, String name, String headerText, CollectionRenderer<E> renderer,
      CollectionEditor<E> editor, Comparator<E> comparator, ColumnSizePolicy sizePolicy, String tooltip,
      ColumnTooltipProvider<E> tooltipProvider, boolean reorderable, Convertor<? super E, V> convertor,
      UserDataHolder userData)
    {
      myId = id;
      myName = name;
      myHeaderText = headerText;
      myRenderer = renderer;
      myEditor = editor;
      myComparator = comparator;
      myPrefSizePolicy = sizePolicy;
      myTooltip = tooltip;
      myTooltipProvider = tooltipProvider;
      myReorderable = reorderable;
      myConvertor = convertor;
      myUserData = userData;
    }

    public String toString() {
      return myHeaderText == null ? myId : myHeaderText;
    }

    public String getId() {
      return myId;
    }

    public String getName() {
      return myName;
    }

    public String getColumnHeaderText() {
      return myHeaderText;
    }

    public CollectionRenderer<E> getDataRenderer() {
      return myRenderer;
    }

    public CollectionEditor<E> getDataEditor() {
      return myEditor;
    }

    public Comparator<E> getComparator() {
      return myComparator;
    }

    public int getPreferredWidth(JTable table, ATableModel<E> tableModel, ColumnAccessor<E> renderingAccessor,
      int columnIndex)
    {
      return myPrefSizePolicy != null ?
        myPrefSizePolicy.getPreferredWidth(table, tableModel, renderingAccessor, columnIndex) : -1;
    }

    public ColumnSizePolicy getSizePolicy() {
      return myPrefSizePolicy;
    }

    public String getHeaderTooltip() {
      return myTooltip;
    }

    public boolean isOrderChangeAllowed() {
      return myReorderable;
    }

    public boolean isSortingAllowed() {
      return myComparator != null;
    }

    public V getValue(E object) {
      return myConvertor.convert(object);
    }

    public ColumnTooltipProvider<E> getTooltipProvider() {
      return myTooltipProvider;
    }

    public <T> T getUserData(TypedKey<T> key) {
      return myUserData.getUserData(key);
    }

    public <T> void putUserDate(TypedKey<T> key, T data) {
      myUserData.putUserDate(key, data);
    }

    public <T> T getHint(@NotNull TypedKey<T> key) {
      return null;
    }
  }
}
