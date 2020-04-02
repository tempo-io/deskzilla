package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.engine.gui.LeftFieldsBuilder;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.advmodel.*;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.renderer.FontStyle;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.components.renderer.table.*;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.util.List;

public class DropDown extends Select<BugzillaCustomField.ResolvedCustomFieldOption, Long> {
  public DropDown(DBAttribute<Long> attribute, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, BoolExpr<DP> optionsFilter, ModelKey<ResolvedCustomFieldOption> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, order, fields, optionsFilter, modelKey);
  }

  public JComponent createValueEditor(ConnectContext context) {
    AComboBox<ItemKey> comboBox = new AComboBox<ItemKey>(5);
    ModelKey<ResolvedCustomFieldOption> key = getModelKey();
    DefaultUIController.connectWithKey(comboBox, key);
    DefaultUIController.RECENT_CONFIG.putClientValue(comboBox, key.getName());
    return comboBox;
  }

  public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
    return new TextCell(FontStyle.BOLD, new LeftFieldsBuilder.ItemTextGetter(getModelKey()));
  }

  protected void buildKey(BaseKeyBuilder<ResolvedCustomFieldOption> builder) {
    builder.setValueRenderer(Renderers.canvasDefault(""));
    builder.setIO(new MyIO());
    builder.setAccessor(new BaseModelKey.SimpleDataAccessor<ResolvedCustomFieldOption>(getId()));
    builder.setExport(BaseModelKey.Export.RENDER);
  }

  @CanBlock
  public ResolvedCustomFieldOption loadValue(ItemVersion version, LoadedItemServices itemServices) {
    Long optionItem = version.getValue(getAttribute());
    if (optionItem == null)
      return null;
    NameResolver resolver = itemServices.getActor(NameResolver.ROLE);
    ResolvedCustomFieldOption option = resolver.getCache().getItemKeyOrNull(optionItem, version.getReader(), BugzillaCustomField.ResolvedCustomFieldOption.FACTORY);
    return option;
  }

  protected void buildColumn(TableColumnBuilder<LoadedItem, ResolvedCustomFieldOption> builder) {
    builder.setValueCanvasRenderer(Renderers.canvasDefault(""));
    builder.setValueComparator(ItemKey.keyComparator());
    builder.setConvertor(new Convertor<LoadedItem, ResolvedCustomFieldOption>() {
      public ResolvedCustomFieldOption convert(LoadedItem value) {
        return getModelKey().getValue(value.getValues());
      }
    });
  }

  private static class MyIO implements BaseModelKey.DataIO<ResolvedCustomFieldOption> {
    public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<ResolvedCustomFieldOption> modelKey) {
    }

    public void addChanges(UserChanges changes, ModelKey<ResolvedCustomFieldOption> modelKey) {
      final BugzillaCustomField bcf = BugzillaCustomField.fromModelKey(modelKey);
      if(bcf == null) {
        assert false : modelKey;
        return;
      }
      
      final ResolvedCustomFieldOption value = changes.getNewValue(modelKey);
      if (value != null) {
        final long option = value.getResolvedItem();
        if (option <= 0) {
          changes.getCreator().setValue(bcf.getAttribute(), (Long)null);
        } else {
          changes.getCreator().setValue(bcf.getAttribute(), option);
        }
      }
    }

    public <SM> SM getModel(
      final Lifespan life, final ModelMap model, final ModelKey<ResolvedCustomFieldOption> modelKey, Class<SM> aClass)
    {
      if (!aClass.isAssignableFrom(AComboboxModel.class)) {
        assert false : aClass;
        return null;
      }

      final BugzillaCustomField bcf = BugzillaCustomField.fromModelKey(modelKey);
      if (!(bcf instanceof Select)) {
        assert false : bcf;
        return null;
      }
      final Select sel = (Select) bcf;
      final EnumConstraintType descriptor = sel.getEnumConstraintType();
      final AListModel<ItemKey> variants = sel.getVariantsModel(new ConnectContext(life, model));

      final ResolvedCustomFieldOption value = modelKey.getValue(model);
      final SelectionInListModel<ItemKey> cbModel = SelectionInListModel.create(life, variants, value);
      cbModel.addSelectionListener(life, new SelectionListener.SelectionOnlyAdapter() {
        public void onSelectionChanged() {
          if (!life.isEnded()) {
            ItemKey item = cbModel.getSelectedItem();
            ItemKey prevValue = modelKey.getValue(model);
            if (item != null && !Util.equals(item, prevValue)) {
              @SuppressWarnings({"ConstantConditions"})
              List<ResolvedItem> resolvedList = descriptor.resolveKey(item, new ItemHypercubeImpl());
              if (resolvedList.size() != 1) {
                assert false : resolvedList + " " + item + " " + descriptor;
                Log.warn("cannot resolve value " + resolvedList + " " + item + " " + descriptor);
              } else {
                ResolvedItem resolved = resolvedList.get(0);
                if (!(resolved instanceof ResolvedCustomFieldOption)) {
                  assert false : resolved + " " + item + " " + descriptor;
                  Log.warn("bad value " + resolved + " " + item + " " + descriptor);
                } else {
                  PropertyMap props = new PropertyMap();
                  modelKey.takeSnapshot(props, model);
                  modelKey.setValue(props, (ResolvedCustomFieldOption) resolved);
                  modelKey.copyValue(model, props);
                }
              }
            }
          }
        }
      });

      //noinspection unchecked
      return (SM) cbModel;
    }
  }
}
