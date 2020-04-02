package com.almworks.util.ui;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;
import org.almworks.util.detach.*;

import java.util.List;

public abstract class CompositeDialogEditor implements DialogEditor {
  private final List<DialogEditor> myEditors = Collections15.arrayList();

  protected abstract void disposeComposite();

  protected void addEditor(DialogEditor editor) {
    myEditors.add(editor);
  }

  public boolean isModified() {
    for (DialogEditor editor : myEditors) {
      if (editor.isModified())
        return true;
    }
    return false;
  }

  public void apply() throws CantPerformExceptionExplained {
    for (DialogEditor editor : myEditors) {
      editor.apply();
    }
  }

  public void reset() {
    for (DialogEditor editor : myEditors) {
      editor.reset();
    }
  }

  public final void dispose() {
    disposeComposite();
    for (DialogEditor editor : myEditors) {
      editor.dispose();
    }
  }

  public Detach addAWTChangeListener(ChangeListener listener) {
    DetachComposite detach = new DetachComposite();
    addAWTChangeListener(detach, listener);
    return detach;
  }

  public void addAWTChangeListener(Lifespan life, ChangeListener listener) {
    for (DialogEditor editor : myEditors) 
      editor.addAWTChangeListener(life, listener);
  }

  public void addChangeListener(Lifespan life, ChangeListener listener) {
    for (DialogEditor editor : myEditors) {
      editor.addChangeListener(life, listener);
    }
  }

  public void addChangeListener(Lifespan life, ThreadGate gate, ChangeListener listener) {
    for (DialogEditor editor : myEditors) {
      editor.addChangeListener(life, gate, listener);
    }
  }
}
