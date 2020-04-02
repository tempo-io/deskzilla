package com.almworks.actions;

import com.almworks.api.edit.EditLifecycle;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AToolbar;
import com.almworks.util.ui.actions.*;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifecycle;

import javax.swing.*;

/**
 * @author dyoma
 */
public class CommitStatus extends JLabel {
  private final Lifecycle myCycle = new Lifecycle(false);
  private final ChangeListener myListener = new ChangeListener() {
    public void onChange() {
      updateLabel();
    }
  };

  private void updateLabel() {
    boolean committing = false;
    try {
      committing = getObject(EditLifecycle.ROLE).isDuringCommit();
    } catch (CantPerformException e) {
      setVisible(true);
      setText("Loading\u2026");
      return;
    }
    setVisible(committing);
    if (committing) {
      setText("Saving changes\u2026");
    }
  }

  public void addNotify() {
    super.addNotify();
    if (myCycle.cycleStart()) {
      if (!watchRole(EditLifecycle.MODIFIABLE)) assert false;
    }
    updateLabel();
  }

  private boolean watchRole(TypedKey<?> role) {
    assert myCycle.isCycleStarted();
    DataProvider provider = new DefaultActionContext(this).findProvider(role);
    if (provider != null) {
      provider.addRoleListener(myCycle.lifespan(), role, myListener);
      return true;
    }
    return false;
  }

  private <T> T getObject(TypedKey<T> role) throws CantPerformException {
    return new DefaultActionContext(this).getSourceObject(role);
  }

  public void removeNotify() {
    myCycle.cycleEnd();
    super.removeNotify();
  }

  public static void addToToolbar(AToolbar toolbar) {
    toolbar.addSeparator();
    toolbar.add(new CommitStatus());
  }
}
