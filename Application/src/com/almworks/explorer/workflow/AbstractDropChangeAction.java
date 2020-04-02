package com.almworks.explorer.workflow;

import com.almworks.api.actions.*;
import com.almworks.api.application.*;
import com.almworks.api.application.field.ItemField;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.gui.AbstractComboBoxModelKey;
import com.almworks.api.gui.FrameBuilder;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.EnumConstraintKind;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.util.SyncAttributes;
import com.almworks.tags.TagsComponent;
import com.almworks.util.Terms;
import com.almworks.util.collections.*;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.treeSet;

public abstract class AbstractDropChangeAction extends BaseEditAction {
  protected final Connection myConnection;

  private final boolean myMove;
  private final ItemHypercube myTarget;
  private final DropChangeEditorWindow myEditor;

  protected AbstractDropChangeAction(Connection connection, String frameId, ItemHypercube target, boolean move) {
    super(NON_ZERO);
    myEditor = new DropChangeEditorWindow(frameId);
    myMove = move;
    myTarget = target;
    myConnection = connection;
  }

  @Override
  @NotNull
  public EditorWindowCreator getWindowCreator() {
    return myEditor;
  }

  public boolean isMove() {
    return myMove;
  }

  protected abstract List<? extends EditPrimitive> getActionFields(ItemHypercube cleared, List<ItemWrapper> items);

  protected abstract BaseEnumConstraintDescriptor getStaticDescriptor(DBAttribute<?> axis);

  protected abstract ItemHypercube clearUnchangeableFields(ItemHypercube target, List<ItemWrapper> items) throws CantPerformExceptionExplained;

  protected abstract ItemHypercube clearDependentFieldValues(ItemHypercube target, List<ItemWrapper> items) throws CantPerformExceptionExplained;


  protected void updateEnabledAction(UpdateContext context, List<ItemWrapper> items) throws CantPerformException {
    ItemHypercube cleared = clearCube(myTarget, items);
    if (cleared.getAxisCount() == 0)
      throw new CantPerformExceptionExplained("nothing to change");
    context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION, buildDescription(items, cleared));
    context.setEnabled(true);
  }

  protected ItemHypercube clearCube(ItemHypercube target, List<ItemWrapper> items) throws CantPerformExceptionExplained {
    if (items.isEmpty())
      return target;
    target = clearConnection(target, items);
    target = clearUnchangeableFields(target, items);
    target = clearNotChangingFields(target, items);
    target = clearDependentFieldValues(target, items);
    target = clearEnumValuesNotInModel(target);
    return target;
  }

  private final ItemHypercube clearEnumValuesNotInModel(ItemHypercube target) throws CantPerformExceptionExplained {
    for (DBAttribute<?> axis : target.getAxes()) {
      BaseEnumConstraintDescriptor descriptor = getStaticDescriptor(axis);
      if (descriptor != null) {
        LongList modelValues = LongSet.collect(UiItem.GET_ITEM, descriptor.getAllValues(target));
        SortedSet<Long> replacementValues = treeSet(target.getIncludedValues(axis));
        int mvi = 0;
        int mvsz = modelValues.size();
        for (Iterator<Long> i = replacementValues.iterator(); i.hasNext();) {
          mvi = modelValues.binarySearch(i.next(), mvi, mvsz);
          if (mvi < 0) {
            i.remove();
            mvi = -mvi - 1;
          }
        }
        target = replaceAxis(target, axis, replacementValues);
        if (replacementValues.isEmpty()) {
          throw new CantPerformExceptionExplained("No valid values for " + descriptor.getDisplayName(), "No valid values");
        }
      }
    }
    return target;
  }

  protected BaseEnumConstraintDescriptor getDescriptor(DBAttribute<?> axis) {
    BaseEnumConstraintDescriptor r = getStaticDescriptor(axis);
    if (r != null)
      return r;
    ItemField field = myConnection.getContext().getCustomFields().getByAttribute(axis);
    if (field != null) {
      ConstraintDescriptor d = field.getDescriptor();
      if (d instanceof BaseEnumConstraintDescriptor) {
        return (BaseEnumConstraintDescriptor) d;
      }
    }
    TagsComponent tags = Context.get(TagsComponent.class);
    if (tags != null && TagsComponent.TAGS.equals(axis)) {
      return (BaseEnumConstraintDescriptor) tags.getDescriptor();
    }
    return null;
  }


  private String buildDescription(List<ItemWrapper> items, ItemHypercube cleared) {
    if (items.isEmpty())
      return null;
    ItemWrapper first = items.get(0);
    MetaInfo metaInfo = first.getMetaInfo();
    Connection connection = first.getConnection();
    StringBuilder b = new StringBuilder();
    for (DBAttribute<?> axis : cleared.getAxes()) {
      ModelKey<?> modelKey = metaInfo.findKeyByAttribute(axis, connection);
      if (modelKey == null)
        return null;
      if (b.length() > 0)
        b.append('\n');
      b.append(modelKey.getDisplayableName());
      SortedSet<Long> included = cleared.getIncludedValues(axis);
      if (included == null) {
        b.append(" changed");
        continue;
      }
      BaseEnumConstraintDescriptor descriptor = getDescriptor(axis);
      SortedSet<Long> excluded = cleared.getExcludedValues(axis);
      if (excluded != null) {
        included = new TreeSet<Long>(included);
        included.removeAll(excluded);
      }
      if (descriptor == null || included.isEmpty()) {
        b.append(" changed");
        continue;
      }
      String prefix = " = ";
      for (Long ptr : included) {
        ResolvedItem ra = descriptor.findForItem(ptr);
        if (ra == null)
          continue;
        b.append(prefix).append(ra.getDisplayName());
        prefix = " | ";
      }
      if (included.size() == 1 && (descriptor.getKind() instanceof EnumConstraintKind.IntersectionEnumKind)) {
        boolean clear = isClearAction(included, descriptor);
        EnumConstraintKind.IntersectionEnumKind kind = (EnumConstraintKind.IntersectionEnumKind) descriptor.getKind();
        String name = kind.getReadonlyPrimitiveActionName(isMove(), clear);
        b.append(" (").append(name).append(")");
      }
    }
    return b.toString();
  }

  public static boolean isClearAction(Collection<Long> axis, BaseEnumConstraintDescriptor descriptor) {
    if (axis.size() != 1) return false;
    Long v = axis.iterator().next();
    ResolvedItem missing = descriptor.getMissingItem();
    return missing != null && missing.getResolvedItem() == v;
  }

  @Override
  protected boolean isWindowNeeded(List<ItemWrapper> items) throws CantPerformException {
    ItemHypercube cleared = clearCube(myTarget, items);
    CantPerformException.ensure(cleared.getAxisCount() != 0);
    boolean showDialog = false;
    for (DBAttribute<?> axis : cleared.getAxes()) {
      SortedSet<Long> included = cleared.getIncludedValues(axis);
      if (included == null || included.size() != 1) {
        showDialog = true;
        break;
      }
    }
    return showDialog;
  }

  protected static ItemHypercube clearCube(ItemHypercube target, List<ItemWrapper> items,
    DBAttribute<?> attribute, Convertor<ItemWrapper, ?> check, String what, boolean mustFit)
    throws CantPerformExceptionExplained
  {
    SortedSet<Long> included = target.getIncludedValues(attribute);
    SortedSet<Long> excluded = target.getExcludedValues(attribute);
    if (included == null && excluded == null)
      return target;
    for (ItemWrapper wrapper : items) {
      Object value = check.convert(wrapper);
      boolean fits;
      if (value == null) {
        fits = included == null;
      } else if (value instanceof ResolvedItem || value instanceof Long) {
        Long av = value instanceof Long ? ((Long) value) : ((ResolvedItem) value).getResolvedItem();
        fits = (included == null || included.contains(av)) && (excluded == null || !excluded.contains(av));
      } else if (value instanceof List) {
        List list = (List) value;
        boolean includeOk = included == null;
        boolean excludeOk = true;
        for (Object elem : list) {
          if (elem instanceof ResolvedItem) {
            Long av = ((ResolvedItem) elem).getResolvedItem();
            if (excluded != null && excluded.contains(av)) {
              excludeOk = false;
              break;
            }
            if (!includeOk && included.contains(av)) {
              includeOk = true;
            }
          } else {
            assert false : elem;
          }
        }
        fits = includeOk && excludeOk;
      } else {
        assert false : value;
        fits = true;
      }

      if (!fits) {
        if (mustFit) {
          throw new CantPerformExceptionExplained("cannot change " + what);
        } else {
          return target;
        }
      }
    }
    return replaceAxis(target, attribute, null);
  }

  protected static ItemHypercubeImpl replaceAxis(ItemHypercube target, DBAttribute<?> attribute,
    SortedSet<Long> replacement)
  {
    ItemHypercubeImpl r = new ItemHypercubeImpl();
    for (DBAttribute<?> axis : target.getAxes()) {
      if (!attribute.equals(axis)) {
        SortedSet<Long> included = target.getIncludedValues(axis);
        if (included != null)
          r.addAxisIncluded(axis, included);
        SortedSet<Long> excluded = target.getExcludedValues(axis);
        if (excluded != null)
          r.addAxisExcluded(axis, excluded);
      } else {
        if (replacement != null && !replacement.isEmpty()) {
          r.addAxisIncluded(attribute, replacement);
        }
      }
    }
    return r;
  }

  protected static ItemHypercube clearNotChangingFields(ItemHypercube target, List<ItemWrapper> items)
    throws CantPerformExceptionExplained
  {
    ItemWrapper first = items.get(0);
    MetaInfo metaInfo = first.getMetaInfo();
    Connection connection = first.getConnection();
    if (connection == null) {
      assert false : first;
      return target;
    }
    Set<DBAttribute<?>> axes = target.getAxes();
    for (DBAttribute<?> axis : axes) {
      ModelKey<?> mk = metaInfo.findKeyByAttribute(axis, connection);
      if (mk != null) {
        target = clearCube(target, items, mk, axis, false);
      }
    }
    return target;
  }

  protected static ItemHypercube clearCube(ItemHypercube target, List<ItemWrapper> items,
    ModelKey<?> mk, DBAttribute<?> attribute, boolean mustFit) throws CantPerformExceptionExplained
  {
    return clearCube(target, items, attribute, new GetModelKey(mk), mk.getDisplayableName(), mustFit);
  }

  protected static ItemHypercube clearConnection(ItemHypercube target, List<ItemWrapper> items) throws CantPerformExceptionExplained {
    return clearCube(target, items, SyncAttributes.CONNECTION, ItemWrapper.GET_CONNECTION_ITEM, "Connection", true);
  }

  protected static class GetModelKey extends Convertor<ItemWrapper, Object> {
    private final ModelKey<?> myKey;
    private final boolean myCompareWithAbsent;

    public GetModelKey(ModelKey<?> key) {
      myKey = key;
      if (key instanceof AbstractComboBoxModelKey) {
        myCompareWithAbsent = ((AbstractComboBoxModelKey) key).allowsAbsent();
      } else {
        myCompareWithAbsent = false;
      }
    }

    public Object convert(ItemWrapper value) {
      Object o = value.getModelKeyValue(myKey);
      return myCompareWithAbsent && ItemKeyStub.ABSENT.equals(o) ? null : o;
    }
  }

  @Override
  protected EditCommit createCommit(LongList editedItems, List<ItemWrapper> primaryItems, ActionContext context) throws CantPerformException {
    @SuppressWarnings({"ConstantConditions"})
    ItemEditorUi editor = myEditor.createImmediateCommitEditor(primaryItems, context);
    return EditorContent.createCommitProcedure(Functional.convertList(primaryItems, ItemUiModelImpl.CREATE), editor, context, false);
  }

  class DropChangeEditorWindow extends ModularEditorWindow {
    private final String myFrameId;

    private DropChangeEditorWindow(String frameId) {
      myFrameId = frameId;
    }

    @Override
    public String getFrameId() {
      return myFrameId;
    }

    @Override
    public void tuneFrame(FrameBuilder frame) {
      frame.setIgnoreStoredSize(true);
    }

    @Override
    public String getActionTitle(List<ItemWrapper> items) {
      int count = items.size();
      if (count == 1) {
        ItemWrapper item = items.get(0);
        Connection connection = item.getConnection();
        String id = connection != null ? connection.getDisplayableItemId(item) : "<unknown>";
        return Local.parse("Change " + id);
      } else {
        return Local.parse("Change " + count + " " + Terms.ref_Artifacts);
      }
    }

    @Nullable
    @Override
    protected List<? extends EditPrimitive> getActionFields(List<ItemWrapper> items, Configuration configuration) throws CantPerformException {
      ItemHypercube cleared = clearCube(myTarget, items);
      if (cleared.getAxisCount() == 0) {
        return null;
      }
      return AbstractDropChangeAction.this.getActionFields(cleared, items);
    }

    public ItemEditorUi createImmediateCommitEditor(List<ItemWrapper> items, ActionContext context) throws CantPerformException {
      MetaInfo metaInfo = ItemActionUtils.getUniqueMetaInfo(items);
      prepareEditor(context, items, metaInfo);
      return createEditor(null, metaInfo, items, Configuration.EMPTY_CONFIGURATION);
    }
  }
}
