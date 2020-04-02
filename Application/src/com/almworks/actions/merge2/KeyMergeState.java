package com.almworks.actions.merge2;

import com.almworks.api.application.ModelKey;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.DataRole;

/**
 * @author : Dyoma
 */
public class KeyMergeState extends SimpleModifiable {
  public static final DataRole<KeyMergeState> MERGE_STATE = DataRole.createRole(KeyMergeState.class);

  private final ModelKey myKey;
  private boolean myResolved = false;
  private final ItemUiModelImpl myModel;
  private final PropertyMap myRemote;
  private final PropertyMap myBase;
  private final PropertyMap myLocal;

  public KeyMergeState(ModelKey key, ItemUiModelImpl model, PropertyMap remote, PropertyMap base, PropertyMap local) {
    myKey = key;
    myModel = model;
    myRemote = remote;
    myBase = base;
    myLocal = local;
    if (key.hasValue(base))
      myResolved = isRemoteSameValue() && isLocalSameValue();
    else
      myResolved = (!key.hasValue(remote) && !key.hasValue(local)) || key.isEqualValue(remote, local);
  }

  public String getDisplayableName() {
    return myKey.getDisplayableName();
  }

  public void renderStateOn(CellState state, Canvas canvas, PropertyMap values) {
    myKey.getMergePolicy().renderMergeState(myKey, state, canvas, myResolved, values);
  }

  public boolean isRemoteChanged() {
    return !myResolved && !isRemoteSameValue();
  }

  private boolean isRemoteSameValue() {
    return myKey.isEqualValue(myRemote, myBase);
  }

  public boolean isLocalChanged() {
    return !myResolved && !isLocalSameValue();
  }

  private boolean isLocalSameValue() {
    return myKey.isEqualValue(myBase, myLocal);
  }

  public boolean isResolved() {
    return myResolved;
  }

  public void applyRemote() {
    apply(myRemote);
  }

  public void applyLocal() {
    apply(myLocal);
  }

  public void restoreBase() {
    apply(myBase);
  }

  public void apply(PropertyMap version) {
    myKey.getMergePolicy().applyResolution(myKey, myModel.getModelMap(), version);
    markResolved();
  }

  public void markResolved() {
    myResolved = true;
    fireChanged();
  }

  public boolean isConflict() {
    return isLocalChanged() && isRemoteChanged();
  }

  public ModelKey<?> getKey() {
    return myKey;
  }

  public static Condition<KeyMergeState> CHANGED = new Condition<KeyMergeState>() {
    public boolean isAccepted(KeyMergeState value) {
      return !value.myKey.isEqualValue(value.myLocal, value.myRemote) 
      && (value.isLocalChanged() || value.isRemoteChanged());
    }
  };
}
