package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.application.*;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.EngineUtils;
import com.almworks.bugzilla.integration.BugzillaVersion;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

public class FlagsModelKey implements BaseModelKey.DataIO<List<FlagVersion>> {
  private static final TypedKey<List<FlagVersion>> RESTORED_FLAGS = TypedKey.create("allFlags");
  private static final TypedKey<List<EditableFlag>> EDITABLE_FLAGS = TypedKey.create("editableFlagsModel");
  public static final ModelKey<List<FlagVersion>> MODEL_KEY;

  static {
    BaseKeyBuilder<List<FlagVersion>> builder = BaseKeyBuilder.create();
    builder.setDisplayName("Flags");
    builder.setMergePolicy(new ModelMergePolicy.IgnoreEqual() {
      @Override
      public void applyResolution(ModelKey<?> key, ModelMap model, PropertyMap values) {
        copyFlags(model, values);
      }
    });
    builder.setName("allFlags");
    builder.setIO(new FlagsModelKey());
    builder.setAccessor(new FlagsAccessor());
    builder.setExport(new BugFlagsExport());
    builder.setAllValuesRenderer(new CanvasRenderer<PropertyMap>() {
      @Override
      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        final List<FlagVersion> flags = getAllFlags(item, false);
        canvas.appendText(FlagVersion.getSummaryString(flags));
      }
    });
    MODEL_KEY = builder.getKey();
  }

  public static final Convertor<? super PropertyMap,List<FlagVersion>> GET_FLAGS = new Convertor<PropertyMap, List<FlagVersion>>() {
    @Override
    public List<FlagVersion> convert(PropertyMap value) {
      return getAllFlags(value, true);
    }
  };

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<List<FlagVersion>> key) {
    BugzillaContext context = BugzillaUtil.getContext(itemServices);
    if (context == null) {
      Log.error("FMK: no context ");
      assert false;
      return;
    }
    PrivateMetadata pm = context.getPrivateMetadata();
    List<FlagVersion> flags = FlagVersion.load(itemVersion, pm);
    if (flags.isEmpty()) return;
    values.put(RESTORED_FLAGS, flags);
  }

  public void addChanges(UserChanges changes, ModelKey<List<FlagVersion>> key) {
    List<EditableFlag> editable = changes.getNewValue(EDITABLE_FLAGS);
    if (editable == null) {
      Log.error("No flags changed");
      return;
    }
    Connection connection = changes.getConnection();
    if (!(connection instanceof BugzillaConnection)) return;
    BugzillaContext context = ((BugzillaConnection) connection).getContext();
    long thisUser = EngineUtils.getConnectionUser(connection, changes.getReader());
    ItemVersionCreator bug = changes.getCreator();
    for (EditableFlag flag : editable) {
      if (!flag.isEdited()) continue;
      ItemVersionCreator creator;
      if (flag.isCreated()) {
        creator = bug.createItem();
        creator.setValue(DBAttribute.TYPE, Flags.KIND_FLAG);
        creator.setValue(Flags.AT_FLAG_MASTER, bug.getItem());
        creator.setValue(SyncAttributes.CONNECTION, connection.getConnectionItem());
        creator.setValue(Flags.AT_FLAG_TYPE, flag.getTypeItem());
      } else creator = bug.changeItem(flag.getItem());
      if (flag.isDeleted()) creator.delete();
      else {
        creator.setAlive();
        char newStatus = flag.getStatus().getChar();
        updateSetter(creator, connection, thisUser, newStatus);
        creator.setValue(Flags.AT_FLAG_STATUS, newStatus);
        ItemKey requestee = flag.getRequestee();
        if (requestee == null) creator.setValue(Flags.AT_FLAG_REQUESTEE, (Long)null);
        else {
          long resolved = requestee.getResolvedItem();
          if (resolved <= 0) resolved = User.getOrCreateFromUserInput(bug, requestee.getId(), context.getPrivateMetadata());
          creator.setValue(Flags.AT_FLAG_REQUESTEE, resolved);
        }
      }
    }
  }

  private void updateSetter(ItemVersionCreator creator, Connection connection, long thisUser, char newStatus) {
    BugzillaVersion version = BugzillaVersion.parse(
      creator.forItem(connection.getConnectionItem()).getValue(CommonMetadata.attrBugzillaVerison));
    if (version != null && BugzillaVersion.V4_2.compareTo(version) <= 0) {
      Character prevStatus = creator.getValue(Flags.AT_FLAG_STATUS);
      if (prevStatus != null && prevStatus == newStatus) return;
    }
    creator.setValue(Flags.AT_FLAG_SETTER, thisUser);
  }

  public <SM> SM getModel(Lifespan life, ModelMap model, ModelKey<List<FlagVersion>> key, Class<SM> aClass) {
    return null;
  }

  @NotNull
  public static List<FlagVersion> getAllFlags(PropertyMap item, boolean includeDeleted) {
    return filterOutDeleted(item.get(RESTORED_FLAGS), includeDeleted);
  }

  @NotNull
  public static List<FlagVersion> getAllFlags(ModelMap model, boolean includeDeleted) {
    return filterOutDeleted(model.get(RESTORED_FLAGS), includeDeleted);
  }

  @NotNull
  private static List<FlagVersion> filterOutDeleted(@Nullable List<FlagVersion> original, boolean includeDeleted) {
    List<FlagVersion> flags = original != null ? original : Collections15.<FlagVersion>emptyList();
    if (includeDeleted || flags.isEmpty()) return flags;
    List<FlagVersion> filtered = Collections15.arrayList();
    for (FlagVersion flag : flags) {
      if (!flag.isDeleted()) filtered.add(flag);
    }
    return filtered;
  }

  @Nullable
  public static List<EditableFlag> getEditFlagState(ModelMap map) {
    List<EditableFlag> flags = getEditableFlags(map);
    return flags != null ? Collections.unmodifiableList(flags) : null;
  }

  private static List<EditableFlag> getEditableFlags(ModelMap map) {
    return map.get(EDITABLE_FLAGS);
  }

  private static List<EditableFlag> getEditableFlags(PropertyMap values) {
    return values.get(EDITABLE_FLAGS);
  }

  @NotNull
  private static List<EditableFlag> getOrCreateEditableFlags(ModelMap model) {
    List<EditableFlag> flags = getEditableFlags(model);
    if (flags == null) {
      createEditableFlags(model);
      flags = getEditableFlags(model);
    }
    return flags;
  }

  private static void copyFlags(ModelMap model, PropertyMap values) {
    List<EditableFlag> editable = getOrCreateEditableFlags(model);
    List<EditableFlag> source = getEditableFlags(values);
    if (source == null) source = EditableFlag.toEditableList(getAllFlags(values, true));
    source = Collections15.arrayList(source);
    for (Iterator<EditableFlag> it = editable.iterator(); it.hasNext();) {
      EditableFlag flag = it.next();
      long item = flag.getItem();
      if (item < 0) {
        it.remove();
        continue;
      }
      EditableFlag sourceFlag = EditableFlag.findByItem(item, source);
      if (sourceFlag == null) flag.setStatus(FlagStatus.UNKNOWN);
      else {
        source.remove(sourceFlag);
        flag.setRequestee(sourceFlag.getRawRequestee());
        FlagStatus status = sourceFlag.isDeleted() ? FlagStatus.UNKNOWN : sourceFlag.getStatus();
        flag.setStatus(status);
      }
    }
    for (EditableFlag sourceFlag : source) editable.add(sourceFlag.createCopy());
    model.valueChanged(MODEL_KEY);
  }

  public static AListModel<EditableFlag> createEditableModel(Lifespan life, final ModelMap map) throws
    CantPerformExceptionExplained
  {
    Threads.assertAWTThread();
    List<EditableFlag> flags = getEditableFlags(map);
    if (flags == null) {
      createEditableFlags(map);
    }
    final OrderListModel<EditableFlag> model = new OrderListModel<EditableFlag>();
    JointChangeListener listener = new JointChangeListener() {
      protected void processChange() {
        List<EditableFlag> list = getEditableFlags(map);
        List<EditableFlag> localDelete = selectLocalDelete(list);
        if (!localDelete.isEmpty()) {
          list = Collections15.arrayList(list);
          list.removeAll(localDelete);
        }
        model.replaceElementsSet(list);
        model.updateAll();
      }
    };
    map.addAWTChangeListener(life, listener);
    model.addAWTChangeListener(life, new JointChangeListener(listener.getUpdateFlag()) {
      protected void processChange() {
        List<EditableFlag> list = getEditableFlags(map);
        List<EditableFlag> localDelete = selectLocalDelete(list);
        list.clear();
        list.addAll(model.toList());
        list.addAll(localDelete);
        map.valueChanged(MODEL_KEY);
      }
    });
    listener.onChange();
    return model;
  }

  private static List<EditableFlag> selectLocalDelete(List<EditableFlag> flags) {
    List<EditableFlag> result = Collections15.arrayList();
    for (EditableFlag flag : flags)
      if (flag.isDeleted() && flag.isLocalOnly()) result.add(flag);
    return result;
  }

  private static void createEditableFlags(ModelMap map) {
    List<EditableFlag> flags = EditableFlag.toEditableList(getAllFlags(map, true));
    Collections.sort(flags, EditableFlag.TYPE_ORDER);
    map.put(EDITABLE_FLAGS, flags);
  }

  public static void discard(ModelMap map, EditableFlag value) {
    if (value.discard()) {
      List<EditableFlag> flags = getEditableFlags(map);
      flags.remove(value);
      map.valueChanged(MODEL_KEY);
    } else updateModelMap(map, value);
  }

  private static void updateModelMap(ModelMap map, EditableFlag value) {
    List<EditableFlag> flags = getEditableFlags(map);
    if (flags == null || flags.indexOf(value) < 0) Log.error("Editing missing flag");
    map.valueChanged(MODEL_KEY);
  }

  public static void setStatus(ModelMap map, EditableFlag value, FlagStatus status) {
    if (value.setStatus(status)) updateModelMap(map, value);
  }

  public static void setRequestee(ModelMap modelMap, EditableFlag flag, ItemKey requestee) {
    if (flag.setRequestee(requestee)) updateModelMap(modelMap, flag);
  }

  public static EditableFlag createFlag(ModelMap map, FlagTypeItem type, FlagStatus status) {
    EditableFlag flag = EditableFlag.createNew(type, status, BugzillaConnection.getInstance(map));
    List<EditableFlag> flags = getEditableFlags(map);
    flags.add(flag);
    map.valueChanged(MODEL_KEY);
    return flag;
  }

  public static void replaceWithNewCopy(ModelMap map, EditableFlag flag) {
    FlagTypeItem type = flag.getType();
    FlagStatus status = flag.getStatus();
    if (type == null || status == FlagStatus.UNKNOWN) {
      Log.error("Cannot duplicate " + flag);
      return;
    }
    //noinspection ConstantConditions
    if (flag.getSyncState() == SyncState.NEW) {
      Log.error("Cannot replace flag if it isnt cleared on server");
      return;
    }
    List<EditableFlag> list = getEditableFlags(map);
    int index = list.indexOf(flag);
    if (index < 0) {
      Log.error("Missing flag. Cannot duplicate. " + flag);
      return;
    }
    EditableFlag copy = EditableFlag.createNew(type, status, BugzillaConnection.getInstance(map));
    ItemKey requestee = flag.getRawRequestee();
    if (requestee != null) copy.setRequestee(requestee);
    list.add(index + 1, copy);
    discard(map, flag);
  }

  private static class FlagsAccessor extends BaseModelKey.SimpleDataAccessor<List<FlagVersion>> {
    public FlagsAccessor() {
      super(RESTORED_FLAGS);
    }

    @Override
    public void copyValue(ModelMap to, PropertyMap from, ModelKey<List<FlagVersion>> key) {
      if (isEqualValue(to, from))
        return;
      setValue(to, getValue(from));
      List<EditableFlag> copy = getEditableFlags(from);
      if (copy == null) createEditableFlags(to);
      else {
        copy = Collections15.arrayList(copy);
        to.put(EDITABLE_FLAGS, copy);
      }
      to.valueChanged(key);
    }


    @Override
    public void takeSnapshot(PropertyMap to, ModelMap from) {
      super.takeSnapshot(to, from);
      List<EditableFlag> copy = getEditableFlags(from);
      if (copy != null) copy = Collections15.arrayList(copy);
      to.put(EDITABLE_FLAGS, copy);
    }

    @SuppressWarnings({"SimplifiableIfStatement"})
    @Override
    public boolean isEqualValue(ModelMap models, PropertyMap values) {
      if (!super.isEqualValue(models, values)) return false;
      if (hasChanges(models)) return false;
      if (hasChanges(values)) return false;
      if (!isEditableListsEqual(getEditableFlags(models), getEditableFlags(values))) return false;
      return isEqualValue(getAllFlags(models, true), getAllFlags(values, true));
    }

    @SuppressWarnings({"ConstantConditions"})
    @Override
    protected boolean isEqualValue(@Nullable List<FlagVersion> v1, @Nullable List<FlagVersion> v2) {
      if (super.isEqualValue(v1, v2)) return true;
      if (v1 == null) v1 = Collections15.emptyList();
      if (v2 == null) v2 = Collections15.emptyList();
      if (v1.size() != v2.size()) return false;
      for (int i = 0; i < v1.size(); i++) {
        FlagVersion f1 = v1.get(i);
        FlagVersion f2 = v2.get(i);
        if (f1.getItem() != f2.getItem() || !f1.isEqualState(f2)) return false;
      }
      return true;
    }

    private boolean hasChanges(PropertyMap values) {
      List<EditableFlag> editable = getEditableFlags(values);
      List<FlagVersion> initial = getValue(values);
      return hasChanges(initial, editable);
    }

    private boolean hasChanges(ModelMap models) {
      List<EditableFlag> editable = getEditableFlags(models);
      List<FlagVersion> initial = getValue(models);
      return hasChanges(initial, editable);
    }

    private boolean hasChanges(List<FlagVersion> initial, List<EditableFlag> editable) {
      if (editable == null) return false;
      if (initial == null) initial = Collections15.emptyList();
      return initial.size() != editable.size();
    }

    @SuppressWarnings({"SimplifiableIfStatement"})
    @Override
    public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
      if (!super.isEqualValue(values1, values2)) return false;
      List<EditableFlag> e1 = getEditableFlags(values1);
      List<EditableFlag> e2 = getEditableFlags(values2);
      if (!isEditableListsEqual(e1, e2)) return false;
      return isEqualValue(getAllFlags(values1, true), getAllFlags(values2, true));
    }

    @SuppressWarnings({"SimplifiableIfStatement"})
    private boolean isEditableListsEqual(List<EditableFlag> e1, List<EditableFlag> e2) {
      if (e1 != null && e1.isEmpty()) e1 = null;
      if (e2 != null && e2.isEmpty()) e2 = null;
      if (Util.equals(e1, e2)) return true;
      if (e1 == null) return !hasChanges(e2);
      if (e2 == null) return !hasChanges(e1);
      return false;
    }

    private boolean hasChanges(List<EditableFlag> list) {
      for (EditableFlag flag : list) if (flag.isEdited()) return true;
      return false;
    }

    @Override
    public String matchesPatternString(PropertyMap map) {
      List<FlagVersion> list = getValue(map);
      if (list == null || list.isEmpty()) return "";
      return TextUtil.separate(list, " ", FlagVersion.TO_MATCH_PATTERN_STRING);
    }
  }

  private static class BugFlagsExport implements BaseModelKey.Export<List<FlagVersion>> {
    @Override
    public boolean isExportable(Collection<Connection> connections) {
      for(final Connection conn : connections) {
        BugzillaConnection connection = Util.castNullable(BugzillaConnection.class, conn);
        if(connection != null && connection.hasFlags()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Pair<String, ExportValueType> formatForExport(PropertyMap values, ModelKey<? extends List<FlagVersion>> key, NumberFormat numberFormat, DateFormat dateFormat, boolean htmlAccepted) {
      List<FlagVersion> exported = getAllFlags(values, false);
      Collections.sort(exported, Flag.ORDER);
      String exportString = StringUtil.implode(FlagVersion.TO_BUGZILLA_PRESENTATION_HYPHEN.collectList(exported), "\n");
      return Pair.create(exportString, ExportValueType.LARGE_STRING);
    }
  }
}
