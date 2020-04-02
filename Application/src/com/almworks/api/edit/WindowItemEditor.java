package com.almworks.api.edit;

import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.gui.*;
import com.almworks.edit.EditLifecycleImpl;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.ItemValues;
import com.almworks.util.Pair;
import com.almworks.util.commons.FactoryE;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.Role;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import gnu.trove.TLongObjectHashMap;
import org.almworks.util.Log;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

public class WindowItemEditor<B extends BasicWindowBuilder> implements ItemEditor {
  @NotNull
  private final FactoryE<B, CantPerformException> myWindowCreator;
  private boolean myWindowClosed;
  
  /**
   * Boolean means "we'are alive and waiting for value"
   */
  private volatile Pair<Boolean, WindowController> myWindowController = Pair.create(false, null);
  private final EditControl myEditControl;

  /**
   * @param editControl if you don't have it, you're not locking anything, so you don't need this, use simplified commit
   */
  public WindowItemEditor(@NotNull EditControl editControl, FactoryE<B, CantPerformException> windowCreator) {
    myEditControl = editControl;
    myWindowCreator = windowCreator;
  }

  public WindowItemEditor(@NotNull EditPrepare prepare, FactoryE<B, CantPerformException> windowCreator) {
    this(prepare.getControl(), windowCreator);
  }

  @Override
  public boolean isAlive() {
    Pair<Boolean, WindowController> pair = myWindowController;
    Boolean waitingFor = pair.getFirst();
    WindowController window = pair.getSecond();
    return waitingFor || window != null && window.isVisible();
  }

  private void setNotAlive() {
    myWindowController = Pair.create(false, null);
  }

  @Override
  public void showEditor() throws CantPerformException {
    final B builder = myWindowCreator.create();
    if (builder == null) {
      setNotAlive();
      return;
    }
    final EditLifecycleImpl editLife = EditLifecycleImpl.create(builder, myEditControl);
    setupWindow(builder, editLife);

    myWindowController = Pair.create(true, null);
    (builder.isModal() ? ThreadGate.AWT_QUEUED : ThreadGate.AWT_IMMEDIATE).execute(new Runnable() {
      @Override
      public void run() {
        MutableComponentContainer container = builder.getWindowContainer();
        container.registerActor(Role.role("WindowItemEditor"), WindowItemEditor.this);
        container.registerActorClass(Role.role("WindowControllerGetter"), WindowControllerGetter.class);
        // myWindowController will be set magically after WindowController is registered in the container
        builder.showWindow(new Detach() {
          @Override
          protected void doDetach() throws Exception {
            if (!myWindowClosed) {
              myWindowClosed = true;
              EditControl editControl = editLife.getControl();
              if (editControl != null) editControl.release();
            }
          }
        });
      }
    });
  }

  protected void setupWindow(B builder, EditLifecycleImpl editLife) throws CantPerformException {}

  @Override
  public void activate() throws CantPerformException {
    CantPerformException.ensureNotNull(myWindowController.getSecond()).activate();
  }

  @Override
  public void onEditReleased() {
    if (myWindowClosed) return;
    myWindowClosed = true;
    WindowController window = myWindowController.getSecond();
    if (window != null) {
      window.disableCloseConfirmation(true);
      myWindowController = Pair.create(false, null);
      CantPerformException cannotClose = window.close();
      if (cannotClose != null) {
        Log.warn("NMWIE: cannot close - " + cannotClose);
      }
    }
  }

  @Override
  public void onItemsChanged(TLongObjectHashMap<ItemValues> newValues) {
    // todo Right implementation is not possible: tighter intergration with actual editor required.
    StringBuilder message = new StringBuilder("Concurrent edit: ");
    for (long item : newValues.keys()) {
      message.append(item).append(":(");
      ItemValues values = newValues.get(item);
      message.append(TextUtil.separateToString(values.attributes(), ", ")).append(") ");
    }
    Log.warn(message.toString());
  }

  static class WindowControllerGetter implements Startable {
    public WindowControllerGetter(WindowItemEditor me, WindowController window) {
      me.myWindowController = Pair.create(false, window);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {}
  }

  public static FactoryE<FrameBuilder, CantPerformException> frame(final WindowManager winMan, final String winId) {
    return new FactoryE<FrameBuilder, CantPerformException>() {
      @Override
      public FrameBuilder create() {
        return winMan.createFrame(winId);
      }
    };
  }

  public static FactoryE<FrameBuilder, CantPerformException> frame(final ActionContext context, final String winId) {
    return new FactoryE<FrameBuilder, CantPerformException>() {
      @Override
      public FrameBuilder create() throws CantPerformException {
        return context.getSourceObject(WindowManager.ROLE).createFrame(winId);
      }
    };
  }

  public static FactoryE<DialogBuilder, CantPerformException> dialog(final ActionContext context, final String winId) {
    return new FactoryE<DialogBuilder, CantPerformException>() {
      @Override
      public DialogBuilder create() throws CantPerformException {
        return context.getSourceObject(DialogManager.ROLE).createBuilder(winId);
      }
    };
  }
}
