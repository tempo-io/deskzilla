package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.util.*;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.engine.gui.LeftFieldsBuilder;
import com.almworks.explorer.qbuilder.filter.DateConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.collections.*;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.*;
import com.almworks.util.components.renderer.table.*;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.DocumentFormAugmentor;

import javax.swing.*;
import java.util.Date;
import java.util.TimeZone;

public class DateTime extends BugzillaCustomField<Date, Date> {
  private static final MyRenderer DATETIME_RENDERER = new MyRenderer();

  public DateTime(DBAttribute<Date> attribute, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, ModelKey<Date> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, fields, order, modelKey);
  }

  public boolean isTextSearchEnabled() {
    return false;
  }

  protected ConstraintDescriptor createDescriptor() {
    return new DateConstraintDescriptor(getDisplayName(), getAttribute(), false);
  }

  protected void buildColumn(TableColumnBuilder<LoadedItem, Date> builder) {
    builder.setValueCanvasRenderer(DATETIME_RENDERER);
    builder.setValueComparator(Containers.comparablesComparator());
    builder.setConvertor(new Convertor<LoadedItem, Date>() {
      public Date convert(LoadedItem value) {
        return getModelKey().getValue(value.getValues());
      }
    });
  }

  protected void buildKey(BaseKeyBuilder<Date> builder) {
    builder.setValueRenderer(DATETIME_RENDERER);
    builder.setIO(new DateSimpleIO(getAttribute()));
//    builder.setAccessor(new BaseModelKey.SimpleDataAccessor<ResolvedCustomFieldOption>(getId()));
    builder.setTextCachingValueRenderer(Renderers.shortestDate());
    builder.setExport(BaseModelKey.Export.DATE);
    builder.setComparator(Containers.<Date>comparablesComparator());
  }

  public Date loadValue(ItemVersion version, LoadedItemServices itemServices) {
    return version.getValue(getAttribute());
  }

  public Order createOrder() {
    return null;
  }

  public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
    LeftFieldsBuilder.DateTextGetter getter = new LeftFieldsBuilder.DateTextGetter(getModelKey(), true, true);
    return new TextCell(FontStyle.BOLD, getter);
  }

  public JComponent createValueEditor(ConnectContext context) {
    final ADateField field = new ADateField(DateUtil.LOCAL_DATE_TIME, TimeZone.getDefault());
    DocumentFormAugmentor.DO_NOT_AUGMENT.putClientValue(field, Boolean.TRUE);
    final ModelMap modelMap = context.getModel();
    final ModelKey<Date> modelKey = getModelKey();
    field.setDate(modelKey.getValue(modelMap));
    boolean updateFlag[] = {false};
    field.getDateModel().addAWTChangeListener(context.getLife(), new JointChangeListener(updateFlag) {
      @Override
      protected void processChange() {
        PropertyMap props = new PropertyMap();
        modelKey.takeSnapshot(props, modelMap);
        modelKey.setValue(props, field.getDateModel().getValue());
        modelKey.copyValue(modelMap, props);
      }
    });
    modelMap.addAWTChangeListener(context.getLife(), new JointChangeListener(updateFlag) {
      @Override
      protected void processChange() {
        field.setDate(modelKey.getValue(modelMap));
      }
    });
    return field;
  }

  private static class MyRenderer implements CanvasRenderer<Date> {
    public void renderStateOn(CellState state, Canvas canvas, Date item) {
      if (item != null) {
        canvas.appendText(DateUtil.toLocalDateAndMaybeTime(item, null));
      }
    }
  }
}
