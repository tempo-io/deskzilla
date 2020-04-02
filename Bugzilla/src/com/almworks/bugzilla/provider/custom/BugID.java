package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.field.AdaptiveComparator;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.bugzilla.provider.meta.BugReferenceModelKey;
import com.almworks.engine.gui.LeftFieldsBuilder;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.renderer.FontStyle;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.components.renderer.table.*;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;

import javax.swing.*;
import java.util.Collections;

public class BugID extends BugzillaCustomField<String, Long> {
  public BugID(DBAttribute<Long> attribute, String id, String displayName, Boolean availableOnSubmit,
    BugzillaCustomFields fields, int order, ModelKey<String> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, fields, order, modelKey);
  }

  public boolean isTextSearchEnabled() {
    return false;
  }

  protected void buildColumn(TableColumnBuilder<LoadedItem, String> builder) {
    builder.setValueCanvasRenderer(Renderers.canvasToString());
    builder.setValueComparator(new AdaptiveComparator<Comparable>());
    builder.setConvertor(new Convertor<LoadedItem, String>() {
      public String convert(LoadedItem value) {
        return getModelKey().getValue(value.getValues());
      }
    });
  }

  // hack
  @Override
  protected ModelKey<String> createKey() {
    DBAttribute<Long> attribute = getAttribute();
    return new BugReferenceModelKey(attribute, getDisplayName(), Collections.singletonMap(SOURCE_CUSTOM_FIELD, this), attribute.getId());
  }

  @Override
  protected void buildKey(BaseKeyBuilder<String> stringBaseKeyBuilder) {
    assert false;
  }

  @CanBlock
  public String loadValue(ItemVersion version, LoadedItemServices itemServices) {
    PropertyMap props = new PropertyMap();
    getModelKey().extractValue(version, itemServices, props);
    return getModelKey().getValue(props);
  }

  protected ConstraintDescriptor createDescriptor() {
    return null;
  }

  public Order createOrder() {
    return null;
  }

  public JComponent createValueEditor(ConnectContext context) {
    JTextField field = new JTextField();
    DefaultUIController.connectWithKey(field, getModelKey());
    return field;
  }

  public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
    return new TextCell(FontStyle.BOLD, new LeftFieldsBuilder.TextTextGetter(getModelKey()));
  }
}