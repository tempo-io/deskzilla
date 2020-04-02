package com.almworks.explorer.loader;

import com.almworks.api.actions.CommitModel;
import com.almworks.api.application.LoadedItem;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.gui.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.util.collections.ChangeListener1;
import com.almworks.util.commons.FactoryE;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.tests.GUITestCase;
import com.almworks.util.tests.MockProxy;
import com.almworks.util.ui.actions.*;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import util.concurrent.SynchronizedBoolean;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.almworks.util.Collections15.arrayList;

public class CommitRTests extends ApplicationFixture {
  private final SynchronizedBoolean myCanCloseWindow = new SynchronizedBoolean(false);
  private final MockModelKey<String> myTextKey = new MockModelKey<>(TEXT);
  private final MockModelKey<Integer> myNumKey = new MockModelKey<>(NUM);
  private DialogManager myDialogMan;
  private LoadedItemImpl myLoadedItem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // 1) registers globally MetaInfo for our item type
    registerMetaInfo(myTextKey, myNumKey);
    // 2) uses registered MetaInfo
    createLoadedItem();
    createDialogMan();
  }

  private void createDialogMan() {
    MockProxy<DialogManager> proxy = MockProxy.create(DialogManager.class, "dialogManager");
    proxy.method("showErrorMessage").paramsCount(2).ignore();
    myDialogMan = proxy.getObject();
  }

  /**
   * Starts a windowed edit for {@link #myItem}.
   * Creates a mock window with a window controller that registers close requests.
   * The "editor window" created by {@link EditorFactory} modifies model map and applies the changes, starting the commit (asynchronously).
   * While commit is going on, it is checked that a) EditLifecycle knows about it and b) window cannot be closed.
   * Commit is checked to finish successfully.
   * After it ends, live model is checked to be reloaded with the new values.
   * Also, window is checked to be closed.
   */
  public void testCommitEditedModel() throws InvocationTargetException, InterruptedException, CantPerformException {
    final SynchronizedBoolean closeActionCalled = new SynchronizedBoolean(false);
    final SynchronizedBoolean commitFinishedSuccessfully = new SynchronizedBoolean(false);
    final SynchronizedBoolean loadedItemValuesChecked = new SynchronizedBoolean(false);

    checkLiveItemValuesOnUpdate(INIT_NUM, "newValue", loadedItemValuesChecked, true);

    final EditControl control = ItemSyncSupport.prepareEditOrShowLockingEditor(myManager, LongArray.create(myItem));
    assertNotNull(control);
    final EditLifecycleImpl eli = EditLifecycleImpl.testCreate(control);
    assertTrue(control.start(new EditorFactory() {
      @Override
      public ItemEditor prepareEdit(DBReader reader, final EditPrepare prepare) {
        Log.debug("preparing edit...");
        final ItemUiModelImpl model = myRegistry.createNewModel(myItem, reader);
        return model == null ? null : new WindowItemEditor<>(prepare, new FactoryE<BasicWindowBuilder, CantPerformException>() {
          @Override
          public BasicWindowBuilder create() throws CantPerformException {
            Log.debug("creating window...");
            final WindowController windowController = createWindowController(closeActionCalled);
            Log.debug("window created");
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                Log.debug("running test...");
                myTextKey.setModelValue(model, "newValue");
                AggregatingEditCommit commit = new AggregatingEditCommit();
                commit.addProcedure(null, CommitModel.create(model, myDialogMan));
                commit.addProcedure(null, new EditCommit.Adapter() {
                  @Override
                  public void onCommitFinished(boolean success) {
                    assertTrue(success);
                    myCanCloseWindow.set(true);
                    commitFinishedSuccessfully.set(true);
                  }
                });
                ActionContext ctx = createActionContext(windowController, myManager);
                try {
                  eli.commit(ctx, commit);
                } catch (CantPerformException e) {
                  fail(e.toString());
                }
                assertTrue(eli.isDuringCommit());
                // try to close window: nothing should happen
                windowController.close();
                assertFalse(closeActionCalled.get());
                myTextKey.passAddChanges();
              }
            });
            return createWindowBuilder(windowController);
          }
        });
      }
    }));
    commitFinishedSuccessfully.waitForValue(true);
    Log.debug("1) commit ok;");
    loadedItemValuesChecked.waitForValue(true);
    Log.debug("2) live item reloaded ok;");
    closeActionCalled.waitForValue(true);
    Log.debug("3) window closed.");
  }

  private void checkLiveItemValuesOnUpdate(final Integer expectedNum, final String expectedText, final SynchronizedBoolean checked, final boolean shouldReceiveUpdate) {
    myLoadedItem.addAWTListener(new ChangeListener1<LoadedItem>() {
      @Override
      public void onChange(LoadedItem loadedItem) {
        PropertyMap values = loadedItem.getValues();
        assertEquals(expectedNum, myNumKey.getValue(values));
        assertEquals(expectedText, myTextKey.getValue(values));
        if (!shouldReceiveUpdate) {
          Throwable thr = new Throwable("live item updated");
          System.out.println(thr);
          Log.error(thr);
        }
        checked.set(true);
      }
    });
  }

  private WindowController createWindowController(final SynchronizedBoolean closeActionCalled) {
/*
    JFrame window_ = new JFrame() {
      @Override
      public boolean isVisible() {
        return true;
      }

      @Override
      public void dispose() {
        super.dispose();
        assertTrue(myCanCloseWindow.get());
        closeActionCalled.set(true);
      }
    };
    final WindowController windowController = new WindowControllerImpl(window, new AnActionListener() {
      @Override
      public void perform(ActionContext context) throws CantPerformException {
        fail("no confirmations should be shown");
      }
    });
*/
    MockProxy<WindowController> window = MockProxy.create(WindowController.class, "windowController");
    window.method("isVisible").returning(true).ignore();
    final List<Boolean> disableCloseConfirmation = arrayList();
    window.method("close").perform(new MockProxy.MethodHandler() {
      @Override
      public Object getValue(List arguments) {
        assertEquals(1, disableCloseConfirmation.size());
        if (disableCloseConfirmation.get(0)) {
//        assertTrue(disableCloseConfirmation.get(0));
          assertTrue(myCanCloseWindow.get());
          closeActionCalled.set(true);
        }
        disableCloseConfirmation.clear();
        return null;
      }
    });
    window.method("activate").ignore();
    window.method("disableCloseConfirmation").paramsCount(1).consumeParametersTo(disableCloseConfirmation);
//    window.method("detachOnDispose").paramsCount(1).ignore();

    return window.getObject();
  }

  private BasicWindowBuilder createWindowBuilder(WindowController windowController) {
    MockProxy<BasicWindowBuilder> builder = MockProxy.create(BasicWindowBuilder.class, "windowBuilder");
    MutableComponentContainer windowContainer = myContainer.createSubcontainer("windowBuilder");
    windowContainer.registerActor(WindowController.ROLE, windowController);
    builder.method("getWindowContainer").returning(windowContainer).ignore();
    builder.method("showWindow").paramsCount(1).ignore();
    builder.method("showWindow").paramsCount(0).ignore();
    builder.method("isModal").returning(false).ignore();
    builder.method("detachOnDispose").paramsCount(1).ignore();
    builder.method("addProvider").paramsCount(1).ignore();
    builder.method("setCloseConfirmation").paramsCount(1).ignore();
    return builder.getObject();
  }

  private static ActionContext createActionContext(WindowController windowController, SyncManager syncMan) {
    JPanel component = new JPanel();
    SimpleProvider provider = new SimpleProvider(WindowController.ROLE, SyncManager.ROLE);
    provider.setSingleData(WindowController.ROLE, windowController);
    provider.setSingleData(SyncManager.ROLE, syncMan);
    DataProvider.DATA_PROVIDER.putClientValue(component, provider);
    ActionContext ctx = new DefaultActionContext(component);
    return ctx;
  }

  /**
   * Write transaction should be rolled back, values in the model should stay the same.
   */
  public void _testProblemWhileGatheringModelChanges() throws InvocationTargetException, InterruptedException {
    SynchronizedBoolean updatedLiveItem = new SynchronizedBoolean(false);
    final SynchronizedBoolean rolledBack = new SynchronizedBoolean(false);
    // no update should be received
    checkLiveItemValuesOnUpdate(INIT_NUM, INIT_TEXT, updatedLiveItem, false);
    for (int i = 0; i < 5; ++i) {
      final int attempt = i;
      final CountDownLatch commitRequested = new CountDownLatch(1);
      GUITestCase.runInAWTThread(new Runnable() {
        @Override
        public void run() {
          Log.debug("attempt" + attempt);
          final EditControl control = myManager.prepareEdit(myItem);
          assertNotNull(control);
          assertTrue(control.start(new EditorFactory() {
            @Override
            public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) {
              final ItemUiModelImpl model = myRegistry.createNewModel(myItem, reader);
              return model == null ? null : new ItemEditor() {
                @Override
                public void showEditor() throws CantPerformException {
                  myNumKey.setModelValue(model, 42);
                  myTextKey.setModelValue(model, "newValue");
                  SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                      EditCommit commit = CommitModel.create(model, myDialogMan);
                      commit = notifyOnTransactionEnd(commit, Boolean.FALSE, rolledBack);
                      control.commit(commit);
                      myNumKey.passAddChanges();
                      myTextKey.passAddChanges("problem");
                      Log.debug("after passed changes");
                      commitRequested.countDown();
                    }
                  });
                }

                @Override
                public boolean isAlive() {
                  return !rolledBack.get();
                }

                @Override
                public void activate() {
                }

                @Override
                public void onEditReleased() {
                }

                @Override
                public void onItemsChanged(TLongObjectHashMap<ItemValues> newValues) {
                  assert false;
                }
              };
            }
          }));
        }
      });
      commitRequested.await();
      rolledBack.waitForValue(true);
      GUITestCase.flushAWTQueue();
      Thread.sleep(200);
      assertFalse(updatedLiveItem.get());
    }
  }

  public void testActivateReleaseLockingEditor() throws CantPerformException, InterruptedException {
    EditControl control = myManager.prepareEdit(myItem);
    assertNotNull(control);
    assertTrue(
      control.start(new EditorFactory() {
      @Override
      public ItemEditor prepareEdit(DBReader reader, EditPrepare edit) {
        return new ItemEditor() {
          boolean activated;
          boolean released;
          @Override
          public boolean isAlive() {
            return true;
          }

          @Override
          public void showEditor() {
            activateAndRelease();
          }

          @Override
          public void activate() {
            assertTrue(!activated);
            activated = true;
          }

          @Override
          public void onEditReleased() {
            assertTrue(!released);
            released = true;
          }

          @Override
          public void onItemsChanged(TLongObjectHashMap<ItemValues> newValues) {
            assert false;
          }
        };
      }
    }));
  }

  private void activateAndRelease() {
    mustThrow(CantPerformExceptionSilently.class, new RunnableE<CantPerformException>() {
      @Override
      public void run() throws CantPerformException {
        ItemSyncSupport.prepareEditOrShowLockingEditor(myManager, LongArray.create(myItem));
      }
    });
    EditorLock lock = myManager.findLock(myItem);
    assertNotNull(lock);
    lock.release();
  }

  private void createLoadedItem() {
    myLoadedItem = db.readForeground(new ReadTransaction<LoadedItemImpl>() {
      @Override
      public LoadedItemImpl transaction(DBReader reader) throws DBOperationCancelledException {
        return LoadedItemImpl.createLive(Lifespan.FOREVER, myRegistry, myItem, reader);
      }
    }).waitForCompletion();
    assertNotNull(myLoadedItem);
  }
}
