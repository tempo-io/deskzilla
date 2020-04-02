package com.almworks.edit;

import com.almworks.api.actions.EditorCloseConfirmation;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.api.gui.WindowController;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.LongList;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class EditLifecycleImpl implements EditLifecycle {
  private final SimpleModifiable myModifiable = new SimpleModifiable();
  @Nullable
  private final EditControl myEditControl;
  private final AtomicBoolean myDuringCommit = new AtomicBoolean(false);
  private boolean myWindowClosed = false;

  // AWT-confined
  private AnActionListener myCloseConfirmation;
  private static final int CLOSE_CONFIRMATION_ENABLED = 0;
  private static final int CLOSE_CONFIRMATION_DISABLED_CAN_CLOSE = 1;
  private static final int CLOSE_CONFIRMATION_DISABLED_NO_CLOSE = 2;
  private int myCloseConfirmationState = CLOSE_CONFIRMATION_ENABLED;

  private EditLifecycleImpl(EditControl editControl) {
    myEditControl = editControl;
  }

  public static EditLifecycleImpl create(BasicWindowBuilder builder, @Nullable EditControl control) {
    final EditLifecycleImpl life = new EditLifecycleImpl(control);
    builder.detachOnDispose(new Detach() {
      @Override
      protected void doDetach() throws Exception {
        EditControl editControl = life.myEditControl;
        if (editControl != null) editControl.release();
      }
    });
    ConstProvider provider = new ConstProvider();
    provider.addData(ROLE, life);
    provider.addData(MODIFIABLE, life.getModifiable());
    builder.addProvider(provider);
    builder.setCloseConfirmation(new AnActionListener() {
      @Override
      public void perform(ActionContext context) throws CantPerformException {
        if (life.myCloseConfirmationState == CLOSE_CONFIRMATION_ENABLED && life.myCloseConfirmation != null) {
          CantPerformExceptionExplained dontClose = ActionUtil.performSafe(life.myCloseConfirmation, context);
          if (dontClose != null) throw dontClose;
        }
      }
    });
    return life;
  }

  public static EditLifecycleImpl testCreate(EditControl control) {
    return new EditLifecycleImpl(control);
  }

  @Override
  public void commit(ActionContext context, final EditCommit commit, boolean unsafe) throws CantPerformException {
    final WindowController window = context.getSourceObject(WindowController.ROLE);
    AggregatingEditCommit wrapper = AggregatingEditCommit.toAggregating(commit);
    wrapper.addProcedure(ThreadGate.AWT, new EditCommit.Adapter() {
      @Override
      public void onCommitFinished(boolean success) {
        if (success) commitSuccessfullyFinished(window);
        else announceEditing(window);
      }
    });
    setCloseConfirmationState(CLOSE_CONFIRMATION_DISABLED_NO_CLOSE, window);
    if (!myDuringCommit.compareAndSet(false, true)) {
      setCloseConfirmationState(CLOSE_CONFIRMATION_ENABLED, window);
      Log.error("Duplicated commit");
      return;
    }
    myModifiable.fireChanged();
    boolean success = false;
    try {
      if (myEditControl == null || unsafe) {
        SyncManager manager = context.getSourceObject(SyncManager.ROLE);
        if (unsafe) {
          assert myEditControl == null;
          manager.unsafeCommitEdit(wrapper);
        } else manager.commitEdit(wrapper);
      } else if (!myEditControl.commit(wrapper)) return;
      success = true;
    } finally {
      if (!success) {
        myDuringCommit.set(false);
        setCloseConfirmationState(CLOSE_CONFIRMATION_ENABLED, window);
        myModifiable.fireChanged();
      }
    }
    myModifiable.fireChanged();
  }

  private void setCloseConfirmationState(int state, WindowController window) {
    myCloseConfirmationState = state;
    if (state == CLOSE_CONFIRMATION_ENABLED) {
      window.enableCloseConfirmation();
    } else {
      window.disableCloseConfirmation(state == CLOSE_CONFIRMATION_DISABLED_CAN_CLOSE);
    }
  }

  @Override
  public final void commit(ActionContext context, EditCommit commit) throws CantPerformException {
    commit(context, commit, false);
  }

  @Override
  public void discardEdit(ActionContext context) throws CantPerformException {
    Threads.assertAWTThread();
    if (myDuringCommit.get()) {
      Log.warn("Attempt to discard edit during commit");
      return;
    }
    if (myCloseConfirmationState == CLOSE_CONFIRMATION_DISABLED_NO_CLOSE) {
      Log.debug("ELI: no close");
      return;
    }
    WindowController.CLOSE_ACTION.perform(context);
    if (myEditControl != null) myEditControl.release();
  }

  public void setDiscardConfirmation(@NotNull AnActionListener confirmation) {
    Threads.assertAWTThread();
    myCloseConfirmation = confirmation;
  }

  @Override
  public LongList getEditingItems() {
    return myEditControl == null ? LongList.EMPTY : myEditControl.getItems();
  }

  private void announceEditing(WindowController window) {
    if (!myDuringCommit.compareAndSet(true, false)) Log.error("Not during commit");
    setCloseConfirmationState(CLOSE_CONFIRMATION_ENABLED, window);
    myModifiable.fireChanged();
  }

  private void commitSuccessfullyFinished(WindowController window) {
    if (!myDuringCommit.compareAndSet(true, false)) Log.error("Not during commit");
    setCloseConfirmationState(CLOSE_CONFIRMATION_DISABLED_CAN_CLOSE, window);
    myModifiable.fireChanged();
    closeWindow(window);
  }

  protected void closeWindow(WindowController window) {
    if (!myWindowClosed) {
      CantPerformExceptionExplained cannotClose = window.close();
      assert cannotClose == null : window + " " + cannotClose;
      myWindowClosed = cannotClose == null;
    }
  }

  @Override
  public Modifiable getModifiable() {
    return myModifiable;
  }

  @Override
  public void checkCommitAction() throws CantPerformException {
    CantPerformException.ensure(!myDuringCommit.get());
  }

  @Override
  public boolean isDuringCommit() {
    return myDuringCommit.get();
  }

  @Override
  public EditControl getControl() {
    return myEditControl;
  }

  public void setupEditModel(final ItemUiModelImpl model, final AnAction saveAction) {
    model.putService(SERVICE_KEY, this);
    if (saveAction != null)
      setDiscardConfirmation(new EditorCloseConfirmation() {
        @Override
        protected boolean isCloseConfirmationRequired(ActionContext context) throws CantPerformException {
          return model.hasChangesToCommit();
        }

        @Override
        protected void answeredYes(ActionContext context) throws CantPerformException {
          saveAction.perform(context);
        }
      });
  }

  public void setupEditModelWindow(BasicWindowBuilder builder, ItemUiModelImpl model, AnAction saveAction) {
    setupEditModel(model, saveAction);
    SimpleProvider provider = new SimpleProvider(ItemWrapper.ITEM_WRAPPER, ItemUiModelImpl.ROLE);
    provider.setSingleData(ItemWrapper.ITEM_WRAPPER, model);
    provider.setSingleData(ItemUiModelImpl.ROLE, model);
    builder.addProvider(provider);
  }
}
