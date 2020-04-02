package com.almworks.api.explorer.util;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.commons.Function;
import com.almworks.util.components.*;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.GlobalColors;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author dyoma
 */
public class ListSubsetController<T> implements UIController<AList<T>> {
  private final Function<ConnectContext, AListModel<T>> myFullModel;
  private final MultiSelectionAccessorController<T> myController;

  public ListSubsetController(ModelKey<List<T>> key, Function<ConnectContext, AListModel<T>> fullModel) {
    myFullModel = fullModel;
    myController = new MultiSelectionAccessorController<T>(key);
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull AList<T> list) {
    ConnectContext context = new ConnectContext(lifespan, model);
    lifespan.add(list.setCollectionModel(myFullModel.invoke(context)));
    myController.connectUI(context, list.getSelectionAccessor());
  }

  public static void installCompact(CompactSubsetEditor<ItemKey> editor, ModelKey<List<ItemKey>> key,
    ItemKey noSelectionValue, VariantsConfigurator<ItemKey> variants)
  {
    initEditor(editor, noSelectionValue, DefaultUIController.ITEM_KEY_RENDERER);
    editor.setIdentityConvertor(ItemKey.GET_ID);
    CONTROLLER.putClientValue(editor, new Compact(editor, key, variants));
  }

  public static void attachEditor(final CompactSubsetEditor<ItemKey> editor, final ConnectContext context,
    final ModelKey<List<ItemKey>> key)
  {
    JointChangeListener listener = new JointChangeListener() {
      protected void processChange() {
        editor.setSelected(context.getValue(key));
      }
    };
    context.attachModelListener(listener);
    AListModel<ItemKey> valueModel = editor.getSubsetModel();
    valueModel.addAWTChangeListener(context.getLife(), new JointChangeListener(listener.getUpdateFlag()) {
      protected void processChange() {
        context.updateModel(key, editor.getSelectedItems());
      }
    });
  }

  public static void initEditor(CompactSubsetEditor<ItemKey> editor, ItemKey noSelectionValue,
    CanvasRenderer<ItemKey> renderer) {
    editor.setUnknownSelectionItemColor(GlobalColors.ERROR_COLOR);
    editor.setCanvasRenderer(renderer);
    editor.setNothingSelectedItem(noSelectionValue);
  }

  private static class Compact implements UIController<CompactSubsetEditor<ItemKey>>, VariantsAcceptor<ItemKey> {
    private final VariantsConfigurator<ItemKey> myVariants;
    private final CompactSubsetEditor<ItemKey> myEditor;
    private final ModelKey<List<ItemKey>> myKey;

    public Compact(CompactSubsetEditor<ItemKey> editor, ModelKey<List<ItemKey>> key, VariantsConfigurator<ItemKey> variants)
    {
      myEditor = editor;
      myKey = key;
      myVariants = variants;
    }

    public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap modelMap,
      @NotNull CompactSubsetEditor<ItemKey> component)
    {
      final ConnectContext context = new ConnectContext(lifespan, modelMap);
      attachEditor(myEditor, context, myKey);
      myVariants.configure(context, this);
    }

    public void accept(AListModel<ItemKey> variants, @Nullable Configuration recentConfig) {
      myEditor.setFullModel(variants, recentConfig);
    }
  }
}
