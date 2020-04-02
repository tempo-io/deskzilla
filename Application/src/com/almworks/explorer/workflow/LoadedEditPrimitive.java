package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

/**
 * @author dyoma
 */
public abstract class LoadedEditPrimitive<T> implements EditPrimitive<JComponent> {
  private final String myAttribute;
  private final TypedKey<T> myOperation;

  public LoadedEditPrimitive(String attribute, TypedKey<? extends T> operation) {
    myAttribute = attribute;
    myOperation = (TypedKey<T>) operation;
  }

  @NotNull
  protected final ModelKey<?> findKey(MetaInfo metaInfo) throws CantPerformExceptionExplained {
    ModelKey<?> key = metaInfo.findKey(myAttribute);
    if (key == null) {
      throw new CantPerformExceptionExplained(this + ": no attribute " + myAttribute);
    }
    return key;
  }

  @NotNull
  protected final ModelOperation<T> findOperation(MetaInfo metaInfo) throws CantPerformExceptionExplained {
    ModelKey<?> key = findKey(metaInfo);
    ModelOperation<T> operation = key.getOperation(myOperation);
    if (operation == null)
      throw new CantPerformExceptionExplained("Unsupported operation: " + myOperation + " key: " + key);
    return operation;
  }

  protected String getAttributeName() {
    return myAttribute;
  }

  @NotNull
  protected final TypedKey<T> getOperation() {
    return myOperation;
  }

  /**
   * Should return true if the user must be asked to confirm the cancel edit action.
   */
  public boolean isConsiderablyModified(JComponent component) {
    return false;
  }

  public double getEditorWeightY() {
    return isInlineLabel() ? 0 : 1;
  }

  public abstract boolean isApplicable(MetaInfo metaInfo, List<ItemWrapper> items);
}
