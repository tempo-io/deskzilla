package com.almworks.explorer.loader;

import com.almworks.api.application.*;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.engine.Connection;
import com.almworks.integers.LongArray;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.collections.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.DataRole;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.external.BitSet2;

import java.util.*;

/**
 * @author Dyoma
 */
public class ItemUiModelImpl extends SimpleModifiable implements ItemUiModel {
  public static final DataRole<ItemUiModelImpl> ROLE = DataRole.createRole(ItemUiModelImpl.class);

  private final DBStatusHolder myDBStatus;
  private final ModelMapImpl myModels = new ModelMapImpl();
  private final PropertyMap myOriginalValues = new PropertyMap();

  public static final Convertor<ItemWrapper, ItemUiModelImpl> CREATE = new Convertor<ItemWrapper, ItemUiModelImpl>() {
    @Override
    public ItemUiModelImpl convert(ItemWrapper value) {
      return create(value);
    }
  };

  public ItemUiModelImpl(ItemWrapper.DBStatus initialStatus) {
    myDBStatus = new DBStatusHolder(this);
    myDBStatus.setStatus(initialStatus);
    myModels.addChangeListener(Lifespan.FOREVER, this);
  }

  public static ItemUiModelImpl create(ItemWrapper item) {
    ItemUiModelImpl model = new ItemUiModelImpl(item.getDBStatus());
    model.setValues(item.getLastDBValues());
    return model;
  }

  public static List<ItemUiModelImpl> convertList(Collection<? extends ItemWrapper> items) {
    List<ItemUiModelImpl> result = Collections15.arrayList(items.size());
    for (ItemWrapper item : items) result.add(create(item));
    return result;
  }

  public static Detach listenItem(final ItemUiModelImpl model, final LoadedItem item) {
    final ChangeListener1<LoadedItem> listener = new ChangeListener1<LoadedItem>() {
      @Override
      public void onChange(LoadedItem changed) {
        model.myDBStatus.setStatus(item.getDBStatus());
        model.setValues(item.getLastDBValues());
      }
    };
    item.addAWTListener(listener);
    return new Detach() {
      @Override
      protected void doDetach() {
        item.removeAWTListener(listener);
      }
    };
  }

  @Override
  public ModelMap getModelMap() {
    return myModels;
  }

  @Override
  public long getItem() {
    return services().getItem();
  }

  @Override
  @NotNull
  public LoadedItem.DBStatus getDBStatus() {
    DBStatus status = myDBStatus.getStatus();
    assert status != null;
    return status;
  }

  @Override
  public <T> T getModelKeyValue(ModelKey<? extends T> key) {
    return key.getValue(myModels);
  }

  @Override
  public MetaInfo getMetaInfo() {
    return services().getMetaInfo();
  }

  @Override
  public String getItemUrl() {
    return services().getItemUrl();
  }

  @Override
  public boolean isEditable() {
    return !getMetaInfo().getEditBlockKey().getValue(getModelMap());
  }

  @Override
  public PropertyMap getLastDBValues() {
    return myOriginalValues;
  }

  @Override
  public LoadedItemServices services() {
    return LoadedItemServices.VALUE_KEY.getValue(myModels);
  }

  @Override
  public Connection getConnection() {
    return services().getConnection();
  }

  @Override
  public boolean matches(@NotNull Constraint constraint) {
    return LoadedItemImpl.matches(constraint, takeSnapshot());
  }

  public <T> void putService(TypedKey<T> key, T service) {
    myModels.putService(key, service);
  }

  public <T> T getService(TypedKey<T> key) {
    return myModels.getService(key);
  }

  public boolean hasChangesToCommit() {
    return isChanged();
  }

  @Override
  public boolean isChanged() {
    BitSet2 allKeys = ModelKey.ALL_KEYS.getValue(myModels);
    for (int i = allKeys.nextSetBit(0); i >= 0; i = allKeys.nextSetBit(i + 1)) {
      ModelKey<?> key = ModelKeySetUtil.getKey(i);
      if (key != null) {
        if (!key.isEqualValue(myModels, myOriginalValues))
          return true;
      }
    }
    return false;
  }

  public void setValues(PropertyMap values) {
    setValues(values, true);
  }

  private void setValues(PropertyMap values, boolean forceChange) {
    values.copyTo(myOriginalValues);
    ModelKey.ALL_KEYS.copyValue(myModels, myOriginalValues);
    boolean fired = myModels.copyFrom(values);
    if (!fired && forceChange)
      myModels.fireChanged();
  }

  @Override
  public PropertyMap takeSnapshot() {
    PropertyMap snapshot = new PropertyMap();
    BitSet2 allKeys = ModelKey.ALL_KEYS.getValue(myModels);
    for (int i = allKeys.nextSetBit(0); i >= 0; i = allKeys.nextSetBit(i + 1)) {
      ModelKey<?> key = ModelKeySetUtil.getKey(i);
      if (key != null) {
        key.takeSnapshot(snapshot, myModels);
      }
    }

    ModelKey.ALL_KEYS.setValue(snapshot, allKeys);
    return snapshot;
  }

  public void uploadAfterCommit(AggregatingEditCommit commit) {
    commit.addProcedure(null, new EditCommit.Adapter() {
      @Override
      public void onCommitFinished(boolean success) {
        if (success) {
          Connection connection = services().getConnection();
          if (connection != null)
            connection.uploadItems(LongArray.singleton(getItem()));
          else
            assert false;
        }
      }
    });
  }

  public PropertyMap getChanges() {
    PropertyMap changes = new PropertyMap();
    Set<ModelKey<?>> changedKeys = Collections15.hashSet();
    ModelKey.CHANGED_KEYS.setValue(changes, changedKeys);
    BitSet2 allKeys = ModelKey.ALL_KEYS.getValue(myModels);
    for (int i = allKeys.nextSetBit(0); i >= 0; i = allKeys.nextSetBit(i + 1)) {
      ModelKey<?> key = ModelKeySetUtil.getKey(i);
      if (key != null) {
        key.takeSnapshot(changes, myModels);
        if (key.isEqualValue(myModels, myOriginalValues))
          continue;
        changedKeys.add(key);
      }
    }
    return changes;
  }
}
