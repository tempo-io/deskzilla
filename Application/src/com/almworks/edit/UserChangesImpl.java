package com.almworks.edit;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.*;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.properties.Role;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.arrayList;
import static org.almworks.util.Collections15.hashSet;

public class UserChangesImpl implements UserChanges {
  private final PropertyMap myChangedValues;
  private final ItemVersionCreator myItem;
  private final List<ModelKey> myNotReady = Collections15.arrayList();
  // confined to DB thread
  private final List<CommitProblem> myProblems = arrayList();

  public UserChangesImpl(PropertyMap changes, ItemVersionCreator item) {
    myChangedValues = changes;
    myItem = item;
    myNotReady.addAll(ModelKey.CHANGED_KEYS.getValue(myChangedValues));
  }

  @Override
  public <T> T getNewValue(ModelKey<T> key) {
    return key.getValue(myChangedValues);
  }

  @Override
  public <T> T getNewValue(TypedKey<T> key) {
    return myChangedValues.get(key);
  }

  @Override
  public ItemVersionCreator getCreator() {
    return myItem;
  }

  public DBReader getReader() {
    return myItem.getReader();
  }

  @Override
  public void resolveArrayValue(AttributeModelKey<Collection<ItemKey>, Set<Long>> modelKey) {
    Collection<ItemKey> newValue = getNewValue(modelKey);
    Set<Long> dbValue = hashSet();
    boolean failed = false;
    for (ItemKey key : newValue) {
      Long item = null;
      try {
        item = key.resolveOrCreate(this);
      } catch (BadItemKeyException e) {
        addProblem(e.getMessage());
        failed = true;
      }
      if (item != null) {
        dbValue.add(item);
      }
    }
    if (!failed) {
      myItem.setValue(modelKey.getAttribute(), dbValue);
    }
  }

  @Override
  public void invalidValue(ModelKey<?> key, String value) {
    myProblems.add(CommitProblem.invalidValue(key, value));
  }

  @Override
  public void addProblem(String text) {
    myProblems.add(CommitProblem.general(text));
  }

  @Override
  public long getConnectionItem() {
    return Util.NN(myItem.getValue(SyncAttributes.CONNECTION), 0L);
  }

  @Override
  public Connection getConnection() {
    LoadedItemServices services = services();
    return services == null ? null : services.getConnection();
  }

  @Nullable
  private LoadedItemServices services() {
    LoadedItemServices services = LoadedItemServices.VALUE_KEY.getValue(myChangedValues);
    assert services != null;
    return services;
  }

  @Override
  public ItemHypercube getContextHypercube() {
    ItemHypercubeImpl result = new ItemHypercubeImpl();
    // todo :refactoring: here configured projects are not added - ensure that it's ok in JIRA too
    return ItemHypercubeUtils.adjustForConnection(result, getConnection());
  }

  @Override
  public <T> T getActor(Role<T> role) {
    LoadedItemServices services = services();
    return services == null ? null : services.getActor(role);
  }

  @Override
  public ItemHypercubeImpl createConnectionCube() {
    Connection connection = getConnection();
    assert connection != null;
    return ItemHypercubeUtils.createConnectionCube(connection);
  }

  public Collection<? extends ModelKey> getNotReady() {
    return Collections.unmodifiableCollection(myNotReady);
  }

  @NotNull
  public List<CommitProblem> getProblems() {
    return myProblems;
  }
}
