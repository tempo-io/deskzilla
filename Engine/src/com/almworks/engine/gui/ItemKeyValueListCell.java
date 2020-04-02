package com.almworks.engine.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelKey;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.renderer.FontStyle;
import com.almworks.util.components.renderer.RendererContext;
import com.almworks.util.components.renderer.table.*;
import org.almworks.util.Collections15;

import java.util.*;

public class ItemKeyValueListCell<K extends ItemKey, C extends Collection<K>> extends AbstractListCell {
  private static final Convertor<ItemKey, TableRendererCell> TO_LABEL_CELL =
    new Convertor<ItemKey, TableRendererCell>() {
      @Override
      public TableRendererCell convert(ItemKey value) {
        return TextCell.label(value.getDisplayName(), FontStyle.BOLD, true);
      }
    };

  private final ModelKey<C> myKey;
  private final Comparator<? super K> myComparator;

  public ItemKeyValueListCell(
    ModelKey<C> key, Comparator<? super K> comparator, TableRenderer renderer)
  {
    super(renderer, "cells:" + key.getName());
    myKey = key;
    myComparator = comparator;
  }

  public ItemKeyValueListCell(ModelKey<C> key, TableRenderer renderer) {
    this(key, null, renderer);
  }

  @Override
  protected List<TableRendererCell> createCells(RendererContext context) {
    Collection<K> values = LeftFieldsBuilder.getModelValueFromContext(context, myKey);
    if(values == null || values.isEmpty()) {
      return Collections.emptyList();
    }

    if(myComparator != null) {
      final List<K> list = Collections15.arrayList(values);
      Collections.sort(list, myComparator);
      values = list;
    }

    return TO_LABEL_CELL.collectList(values);
  }
}
