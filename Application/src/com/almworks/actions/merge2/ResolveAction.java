package com.almworks.actions.merge2;

import com.almworks.util.L;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;

/**
 * @author : Dyoma
 */
abstract class ResolveAction extends AnAbstractAction {
  protected ResolveAction(String text, Icon icon) {
    super(text, icon);
  }

  public void perform(ActionContext context) throws CantPerformException {
    List<KeyMergeState> selection = context.getSourceCollection(KeyMergeState.MERGE_STATE);
    for (int i = 0; i < selection.size(); i++) {
      KeyMergeState state = selection.get(i);
      if (isApplicable(state))
        perform(state);
    }
  }

  protected abstract void perform(KeyMergeState state);

  public void update(UpdateContext context) throws CantPerformException {
    super.update(context);
    context.watchRole(KeyMergeState.MERGE_STATE);
    List<KeyMergeState> sourceCollection = context.getSourceCollection(KeyMergeState.MERGE_STATE);
    if (sourceCollection.size() == 0) {
      context.setEnabled(EnableState.DISABLED);
      return;
    }
    boolean hasApplicable = false;
    for (Iterator<KeyMergeState> iterator = sourceCollection.iterator(); iterator.hasNext();) {
      KeyMergeState state = iterator.next();
      if (isApplicable(state)) {
        hasApplicable = true;
        break;
      }
    }
    context.setEnabled(hasApplicable ? EnableState.ENABLED : EnableState.DISABLED);
  }

  protected abstract boolean isApplicable(KeyMergeState state);

  public static class ApplyRemote extends ResolveAction {
    public ApplyRemote() {
      super(L.actionName("Take &Remote Value"), Icons.MERGE_ACTION_APPLY_REMOTE);
    }

    protected void perform(KeyMergeState state) {
      state.applyRemote();
    }

    protected boolean isApplicable(KeyMergeState state) {
      return state.isRemoteChanged() || state.isLocalChanged();
    }
  }

  public static class ApplyLocal extends ResolveAction {
    public ApplyLocal() {
      super(L.actionName("Take &Local Value"), Icons.MERGE_ACTION_APPLY_LOCAL);
    }

    protected void perform(KeyMergeState state) {
      state.applyLocal();
    }

    protected boolean isApplicable(KeyMergeState state) {
      return state.isLocalChanged() || state.isRemoteChanged();
    }
  }

  public static class RestoreBase extends ResolveAction {
    public RestoreBase() {
      super(L.actionName("Take &Original Value Without Changes"), Icons.MERGE_ACTION_APPLY_ORIGINAL);
    }

    protected void perform(KeyMergeState state) {
      state.restoreBase();
    }

    @Override
    public void update(UpdateContext context) throws CantPerformException {
      // PLO-724
      context.setEnabled(EnableState.INVISIBLE);
    }

    protected boolean isApplicable(KeyMergeState state) {
      return !state.isResolved();
    }
  }

  public static class MarkResolved extends ResolveAction {
    public MarkResolved() {
      super(L.actionName("&Ignore Conflict"), null);
    }

    protected void perform(KeyMergeState state) {
      state.markResolved();
    }

    protected boolean isApplicable(KeyMergeState state) {
      return !state.isResolved();
    }
  }
}
