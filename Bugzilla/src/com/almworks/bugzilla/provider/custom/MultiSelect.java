package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.engine.gui.ItemKeyValueListCell;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.*;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.components.renderer.table.TableRenderer;
import com.almworks.util.components.renderer.table.TableRendererCell;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.util.List;


public class MultiSelect extends Select<List<ItemKey>, List<Long>> {
  public MultiSelect(DBAttribute<List<Long>> attribute, String id, String displayName, Boolean availableOnSubmit,
    int order, BugzillaCustomFields fields, BoolExpr<DP> optionsFilter, ModelKey<List<ItemKey>> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, order, fields, optionsFilter, modelKey);
    assert attribute.getComposition() == DBAttribute.ScalarComposition.LIST;
  }

  public JComponent createValueEditor(ConnectContext context) {
    //AList<ItemKey> checkBoxList = new AList<ItemKey>();
    Lifespan life = context.getLife();
    AListModel<ItemKey> variants = getVariantsModel(context);

    ACheckboxList<ItemKey> cbl = new ACheckboxList<ItemKey>(variants);
    cbl.setCanvasRenderer(Renderers.defaultCanvasRenderer());
    SpeedSearchController.install(cbl);
    cbl.getSelectionAccessor().ensureSelectionExists();
    final SelectionAccessor<ItemKey> selectionAccessor = cbl.getCheckedAccessor();
    final ModelKey<List<ItemKey>> modelKey = getModelKey();
    final ModelMap modelMap = context.getModel();
    final AListModel<ItemKey> selectionModel = modelKey.getModel(life, modelMap, AListModel.class);
    assert selectionModel != null;
    selectionAccessor.setSelected(selectionModel.toList());
    boolean[] updateFlag = {false};
    modelMap.addAWTChangeListener(life, new JointChangeListener(updateFlag) {
      @Override
      protected void processChange() {
        selectionAccessor.setSelected(selectionModel.toList());
      }
    });
    selectionAccessor.addAWTChangeListener(life, new JointChangeListener(updateFlag) {
      @Override
      protected void processChange() {
        List<ItemKey> value = selectionAccessor.getSelectedItems();
        PropertyMap props = new PropertyMap();
        modelKey.takeSnapshot(props, modelMap);
        modelKey.setValue(props, value);
        modelKey.copyValue(modelMap, props);
      }
    });

//      DefaultUIController.connectWithKey(cbl, modelKey);

    AScrollPane pane = new AScrollPane(cbl);
    pane.setAdaptiveVerticalScroll(true);
    UIUtil.configureBasicScrollPane(pane, 20, 4);
    return pane;
  }

  public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
    return new ItemKeyValueListCell(getModelKey(), renderer);
  }

  protected void buildKey(BaseKeyBuilder<List<ItemKey>> builder) {
    builder.setValueRenderer(Renderers.<ItemKey>canvasDefaultList("", ", "));
    builder.setIO(new MyIO());
    builder.setAccessor(new BaseModelKey.SimpleDataAccessor<List<ItemKey>>(getId()));
    builder.setExport(BaseModelKey.Export.RENDER);
  }

  @CanBlock
  public List<ItemKey> loadValue(ItemVersion version, LoadedItemServices itemServices) {
    List<Long> options = version.getValue(getAttribute());
    if (options == null)
      return null;
    NameResolver resolver = itemServices.getActor(NameResolver.ROLE);
    List<ItemKey> optionKeys = Collections15.arrayList(options.size());
    DBReader reader = version.getReader();
    for (Long option : options) {
      ResolvedCustomFieldOption optionKey = resolver.getCache().getItemKeyOrNull(option, reader, BugzillaCustomField.ResolvedCustomFieldOption.FACTORY);
      if (optionKey != null) {
        optionKeys.add(optionKey);
      }
    }
    return optionKeys;
  }

  protected void buildColumn(TableColumnBuilder<LoadedItem, List<ItemKey>> builder) {
    builder.setValueCanvasRenderer(Renderers.<ItemKey>canvasDefaultList("", ", "));
    //builder.setValueComparator(ItemKey.keyComparator());
    builder.setConvertor(new Convertor<LoadedItem, List<ItemKey>>() {
      public List<ItemKey> convert(LoadedItem value) {
        return getModelKey().getValue(value.getValues());
      }
    });
  }

  private class MyIO extends BaseKeyBuilder.SimpleRefListIO {
    public MyIO() {
      super(com.almworks.bugzilla.provider.custom.MultiSelect.this.getAttribute(),
        BugzillaCustomField.ResolvedCustomFieldOption.FACTORY, null,
        MultiSelect.this.getEnumConstraintType(), null, false);
    }

    public <SM> SM getModel(Lifespan life, final ModelMap model, ModelKey<List<ItemKey>> collectionModelKey,
      Class<SM> aClass)
    {
      if (!aClass.isAssignableFrom(AListModel.class)) {
        assert false : getDisplayName();
      }
      final ModelKey<List<ItemKey>> key = getModelKey();

      final OrderListModel<ItemKey> cbModel = OrderListModel.create(getList(model, getModelKey()));
      ChangeListener listener = new ChangeListener() {
        public void onChange() {
          cbModel.replaceElementsSet(getList(model, key));
        }
      };
      model.addAWTChangeListener(life, listener);
      listener.onChange();
      return (SM) cbModel;
    }
  }
}
