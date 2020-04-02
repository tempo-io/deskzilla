package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.Pair;
import com.almworks.util.Terms;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SubsetModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.ElementVisitor;
import com.almworks.util.commons.Factory;
import com.almworks.util.commons.Function;
import com.almworks.util.components.SubsetEditor;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.Local;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.*;

/**
 * @author Alex
 */
public class EditWatchersAction extends BaseEditAction.EditInWindowAction implements ExplorerComponentActionNotOnToolbar {
  @NotNull
  private final ModelKey<Collection<ItemKey>> myWatcherList;
  @Nullable
  private final ModelKey<Boolean> myWatchingKey;
  @Nullable
  private final ModelKey<Integer> myWatchersCountKey;

  @Nullable
  private final ModelKey<Boolean> myCanEditWatchers;

  private final Function<ItemWrapper, ItemKey> myMeGetter;
  private final Factory<BaseEnumConstraintDescriptor> myConstraintDescriptorFactory;
  private final TextResolver myUserResolver;
  private final ModelKey<?> myArtifactIdKey;
  private final String myWatchersText;
  private final EditorWindowCreator myEditor;

  public EditWatchersAction(ModelKey<Collection<ItemKey>> watcherList, @Nullable ModelKey<Boolean> isWatching,
    @Nullable ModelKey<Integer> watchersCount, @Nullable ModelKey<Boolean> canEditWatchers,
    Function<ItemWrapper, ItemKey> meGetter, Factory<BaseEnumConstraintDescriptor> constraintDescriptor,
    TextResolver userResolver, ModelKey<?> artifactIdKey, String watchersText, Icon icon)
  {
    super(SINGLE_ITEM);
    myEditor = new MyEditor();
    myWatchingKey = isWatching;
    myWatchersCountKey = watchersCount;
    myCanEditWatchers = canEditWatchers;
    myMeGetter = meGetter;
    myConstraintDescriptorFactory = constraintDescriptor;
    myUserResolver = userResolver;
    myArtifactIdKey = artifactIdKey;
    myWatcherList = watcherList;
    myWatchersText = watchersText;
    setDefaultPresentation(PresentationKey.NAME, "Edit " + watchersText + "\u2026");
    setDefaultPresentation(PresentationKey.SMALL_ICON, icon);
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.INVISIBLE);
  }

  @Override
  @NotNull
  public EditorWindowCreator getWindowCreator() {
    return myEditor;
  }

  @Override
  protected void updateEnabledAction(UpdateContext context, List<ItemWrapper> wrappers) throws CantPerformException {
    Boolean canEdit = myCanEditWatchers == null ? Boolean.TRUE : ItemActionUtils.getSameForAllKeyValue(myCanEditWatchers, wrappers);
    context.setEnabled(Boolean.TRUE.equals(canEdit) ? EnableState.ENABLED : EnableState.INVISIBLE);
  }

  class MyEditor extends ModularEditorWindow {
    @Nullable @Override
    protected List<? extends EditPrimitive> getActionFields(List<ItemWrapper> wrappers, Configuration configuration) {
      return Collections15.list(new SubsetField());
    }

    @Override
    protected String getActionTitle(List<ItemWrapper> items) {
      if (items.size() == 1) {
        return "Edit " + myWatchersText + " for " + Local.parse(Terms.ref_Artifact) + " " + String.valueOf(items.get(0).getModelKeyValue(myArtifactIdKey));
      } else {
        return "Edit " + myWatchersText + " for " + items.size() + " " + Terms.ref_Artifacts;
      }
    }

    @Override
    protected String getFrameId() {
      return "changeWatcherList";
    }
  }

  private class SubsetField implements EditPrimitive<JComponent> {
    private SubsetModel<ItemKey> usersSubsetModel;

    public void setValue(ItemUiModel model, JComponent component) throws CantPerformExceptionExplained {
      PropertyMap map = new PropertyMap();
      ModelMap modelMap = model.getModelMap();

      List<ItemKey> unmodifiableImage = usersSubsetModel.getUnmodifiableImage();
      changeMap(myWatcherList, map, modelMap, unmodifiableImage);

      if (myWatchingKey != null) {
        boolean value = usersSubsetModel.contains(myMeGetter.invoke(model));
        changeMap(myWatchingKey, map, modelMap, value);
      }

      if (myWatchersCountKey != null) {
        changeMap(myWatchersCountKey, map, modelMap, unmodifiableImage.size());
      }
    }

    private <T> void changeMap(ModelKey<T> key, PropertyMap map, ModelMap modelMap, T value) {
      key.takeSnapshot(map, modelMap);
      key.setValue(map, value);
      key.copyValue(modelMap, map);
    }

    public String getSaveProblem(JComponent component, MetaInfo metaInfo) {
      return null;
    }

    public Pair<JComponent, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo, List<? extends ItemWrapper> wrappers, PropertyMap additionalProperties) {
      Collection<ItemKey> items;
      if (wrappers.size() != 1) {
        assert false;
        return null;
      }
      final ItemUiModelImpl model = ItemUiModelImpl.create(wrappers.get(0));
      final ModelMap modelMap = model.getModelMap();
      items = myWatcherList.getValue(modelMap);
      if (items == null) {
        Log.warn("update failed");
        items = Collections.emptyList();
      }

      AListModel listModel = getFullModel(lifespan, modelMap);
      usersSubsetModel = SubsetModel.create(lifespan, listModel, false);
      usersSubsetModel.setSubset(items);
      Function<String, ItemKey> creator = new Function<String, ItemKey>() {
        public ItemKey invoke(String argument) {
          return myUserResolver.getItemKey(argument);
        }
      };

      SubsetEditor<ItemKey> subsetEditor = SubsetEditor.create(usersSubsetModel, false, ItemKey.DISPLAY_NAME, creator);
      usersSubsetModel.addChangeListener(lifespan, changeNotifier);
      return Pair.Builder.create(subsetEditor.getComponent(), true);
    }

    private AListModel<ItemKey> getFullModel(Lifespan life, ModelMap model) {
      final ConnectContext context = new ConnectContext(life, model);
      UIControllerUtil.ListModelGetter getter = UIControllerUtil.createListModelGetter(
        myConstraintDescriptorFactory.create(), UIControllerUtil.DEFAULT_CUBE_CONVERTOR);
      getter.excludeMissing();
      AListModel<ItemKey> listModel = getter.invoke(context);
      return listModel;
    }

    public NameMnemonic getLabel(MetaInfo metaInfo) {
      return null;
    }

    public boolean isInlineLabel() {
      return false;
    }

    public JComponent getInitialFocusOwner(JComponent component) {
      return findTextField(component);
    }

    private JComponent findTextField(JComponent root) {
      final JComponent[] result = { root };
      UIUtil.visitComponents(root, JTextComponent.class, new ElementVisitor<JTextComponent>() {
        @Override
        public boolean visit(JTextComponent element) {
          result[0] = element;
          return false;
        }
      });
      return result[0];
    }

    public boolean isConsiderablyModified(JComponent component) {
      return false;
    }

    public void enablePrimitive(JComponent component, boolean enabled) {
    }

    public double getEditorWeightY() {
      return 1;
    }
  }
}
