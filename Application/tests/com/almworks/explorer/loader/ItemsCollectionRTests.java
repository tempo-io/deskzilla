package com.almworks.explorer.loader;

import com.almworks.api.application.*;
import com.almworks.api.engine.ItemSyncProblem;
import com.almworks.api.engine.SyncProblem;
import com.almworks.items.api.*;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.tests.*;
import com.almworks.util.threads.Computable;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

/**
 * @author dyoma
 */
public class ItemsCollectionRTests extends ApplicationFixture {
  private ItemsCollection myCollection;
  private final MockModelKey<String> myKey = new MockModelKey<String>(TEXT);
  private final GetKeyValue myGetLastDbKeyValue = new GetKeyValue("LastDbValue") {
    @Override
    public String get() {
      return myKey.getValue(getSingleLoadedItem().getLastDBValues());
    }
  };
  private final GetKeyValue myGetKeyValue = new GetKeyValue("Value") {
    @Override
    public String get() {
      return myKey.getValue(getSingleLoadedItem().getValues());
    }
  };
  private final GetKeyValue myGetNullKeyValue = new GetKeyValue("Value") {
    @Override
    public String get() {
      LoadedItem li = getSingleOrNoLoadedItem();
      return li == null ? null : myKey.getValue(li.getValues());
    }
  };

  protected void setUp() throws Exception {
    super.setUp();
    registerMetaInfo(myKey);
    myCollection = new ItemsCollection(myRegistry);
    ModelKeySetUtil.cleanupForTest();
  }

  protected void tearDown() throws Exception {
    ModelKeySetUtil.cleanupForTest();
    myRegistry.stop();
    myRegistry = null;
    myCollection = null;
    myKey.setDataPromotionPolicy(DataPromotionPolicy.STANDARD);
    super.tearDown();
  }

  public void testLoading() throws InterruptedException, InvocationTargetException {
    addItemToCollection(myItem);
    GUITestCase.runInAWTThread(new Runnable() {
      public void run() {
        LoadedItem loadedItem = getSingleLoadedItem();
        assertSame(myItem, LoadedItemServices.VALUE_KEY.getValue(loadedItem.getValues()).getItem());
        assertSame(myItem, LoadedItemServices.VALUE_KEY.getValue(loadedItem.getLastDBValues()).getItem());
      }
    });
    Log.debug("initial load ok");
    setValueLaterCheck("newValue", "newValue");
  }

  private void setValueLaterCheck(final String newValue, final String expectedCurValue) throws InvocationTargetException, InterruptedException {
    db.writeForeground(new WriteTransaction<Object>() {
      @Override
      public Object transaction(DBWriter writer) throws DBOperationCancelledException {
        writer.setValue(myItem, TEXT, newValue);
        return null;
      }
    });
    checkMockeyValue(newValue, myGetLastDbKeyValue);
    Log.debug("LDBV guard ok");
    checkMockeyValue(expectedCurValue, myGetKeyValue);
    Log.debug("V guard ok");
  }

  private static void checkMockeyValue(final String expectedValue, final GetKeyValue actualValue) throws InterruptedException {
    TimeGuard.waitFor(ThreadGate.AWT_IMMEDIATE, new Procedure<TimeGuard<Boolean>>() {
      @Override
      public void invoke(TimeGuard<Boolean> guard) {
          if (Util.equals(expectedValue, actualValue.get())) {
          guard.setResult(true);
          Log.debug(actualValue.getName() + " = " + expectedValue + " ok");
        }
      }
    });
  }

  public void testUpdating() throws InterruptedException, InvocationTargetException {
    db.writeForeground(new WriteTransaction<Object>() {
      @Override
      public Object transaction(DBWriter dbAccess) throws DBOperationCancelledException {
        myCollection.addItem(myItem, dbAccess);
        myCollection.addItem(myItem, dbAccess);
        return null;
      }
    }).waitForCompletion();

    myCollection.setLiveMode(false);
    setValueLaterCheck("val-1", "~");

    myKey.setDataPromotionPolicy(DataPromotionPolicy.ALWAYS);
    setValueLaterCheck("val-2", "val-2");

    myKey.setDataPromotionPolicy(DataPromotionPolicy.NEVER);
    setValueLaterCheck("val-3", "val-2");

    myCollection.setLiveMode(true);
    setValueLaterCheck("val-4", "val-4");
  }

  public void testFreezing() throws InterruptedException {
    myCollection.setLiveMode(false);
    final long item = myItem;
    addItemToCollection(item);
    checkMockeyValue(null, myGetNullKeyValue);

    myCollection.setLiveMode(true);
    checkMockeyValue(INIT_TEXT, myGetKeyValue);

    myCollection.removeItems(myItem);
    checkMockeyValue(null, myGetNullKeyValue);

    addItemToCollection(item);
    myCollection.setLiveMode(false);
    checkMockeyValue(INIT_TEXT, myGetKeyValue);

    myCollection.removeItems(myItem);
    checkMockeyValue(INIT_TEXT, myGetKeyValue);

    addItemToCollection(myItem);
    myCollection.setLiveMode(true);
    checkMockeyValue(INIT_TEXT, myGetKeyValue);
  }

  private void addItemToCollection(final long item) {
    db.readForeground(new ReadTransaction<Object>() {
      @Override
      public Object transaction(DBReader reader) throws DBOperationCancelledException {
        myCollection.addItem(item, reader);
        return null;
      }
    }).waitForCompletion();
  }

  public void testAddingSameArtifactTwice() {
    myCollection.setLiveMode(true);
    addItemToCollection(myItem);
    LoadedItem loaded = getSingleLoadedItem();
    assertSame(myItem, loaded.services().getItem());
    addItemToCollection(myItem);
    assertSame(loaded, getSingleOrNoLoadedItem());
    myCollection.setLiveMode(false);
    addItemToCollection(myItem);
    assertSame(loaded, getSingleOrNoLoadedItem());
    myCollection.setLiveMode(true);
    assertSame(loaded, getSingleOrNoLoadedItem());
  }

  public void testUpdateRemovedArtifact() throws InvocationTargetException, InterruptedException {
    myCollection.setLiveMode(true);
    addItemToCollection(myItem);
    LoadedItem loaded = getSingleLoadedItem();
    myCollection.setLiveMode(false);
    myCollection.removeItems(myItem);
    assertSame(loaded, getSingleOrNoLoadedItem());
    setValueLaterCheck("val-1", "~");
    myKey.setDataPromotionPolicy(DataPromotionPolicy.ALWAYS);
    // try to set problem
    myProblems.add(createMockSyncProblem());
    checkMockeyValue("val-1", myGetKeyValue);
  }

  private SyncProblem createMockSyncProblem() {
    MockProxy<ItemSyncProblem> mock = MockProxy.create(ItemSyncProblem.class, "syncProblem").handleObjectMethods();
    mock.method("getItem").returning(myItem).ignore();
    return mock.getObject();
  }

  @NotNull
  private LoadedItem getSingleLoadedItem() {
    LoadedItem li = getSingleOrNoLoadedItem();
    assertNotNull(li);
    return li;
  }
  
  @Nullable
  private LoadedItem getSingleOrNoLoadedItem() {
    return ThreadGate.AWT_IMMEDIATE.compute(new Computable<LoadedItem>() {
      @Override
      public LoadedItem compute() {
        Collection<LoadedItemImpl> listModel = myCollection.getAllInList();
        if (listModel.isEmpty()) return null;
        assertEquals(1, listModel.size());
        LoadedItem loadedItem = listModel.iterator().next();
        assertNotNull(loadedItem);
        return loadedItem;
      }
    });
  }

  private static abstract class GetKeyValue {
    private final String myName;

    private GetKeyValue(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    @Override
    public String toString() {
      return getName() + " " + super.toString();
    }

    public abstract String get();
  }
}