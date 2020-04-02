package com.almworks.engine.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.field.ItemField;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Function2;
import com.almworks.util.components.renderer.table.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;


public abstract class ItemTableBuilder {
  public <T extends ItemKey> void addItem(String label, final ModelKey<T> key) {
    addItem(label, key, null);
  }

  public abstract <T extends ItemKey> void addItem(String label, ModelKey<T> key,
    Function<T, Boolean> visibility);

  public abstract void addString(String label, ModelKey<String> key, boolean hideEmpty);

  public abstract void addString(String label, ModelKey<String> key, Function<String, Boolean> visibility);

  public abstract void addItemList(
    String label, ModelKey<? extends Collection<? extends ItemKey>> key,
    boolean hideEmpty, Comparator<? super ItemKey> order);

  public abstract void addDate(String label, ModelKey<Date> key, boolean hideEmpty, boolean showTime,
    boolean showTimeOnlyIfExists);

  public abstract void addInteger(String label, ModelKey<Integer> key);

  public abstract void addDecimal(String label, ModelKey<BigDecimal> key, boolean hideZeroOrEmpty);

  public abstract void addSeparator();

  public abstract LineBuilder createLineBuilder(String s);

  public abstract void addItemLines(ModelKey<List<ModelKey<?>>> customFields,  final Function2<ModelKey, ModelMap, ItemField> fieldGetter);

  public abstract void addLine(String label, Function<ModelMap, String> valueGetter, Function<ModelMap, Boolean> visibilityFunction);

  public abstract void addSecondsDuration(String label, ModelKey<Integer> modelKey, boolean hideEmpty);

  public abstract void addStringList(String label, ModelKey<List<String>> key, boolean hideEmpty,
    @Nullable Function<Integer, List<CellAction>> cellActions, @Nullable List<CellAction> aggregateActions,
    @Nullable Function2<ModelMap, String, String> elementConvertor);

  public abstract TwoColumnLine addLine(String label, TableRendererCell cell);

  public abstract TableRenderer getPresentation();
}
