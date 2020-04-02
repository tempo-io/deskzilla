package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.util.Terms;
import com.almworks.util.commons.Function;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import java.util.List;

public class ChangeWatchingAction extends ChangeBooleanUserListStateAction {
  public ChangeWatchingAction(ModelKey<Boolean> me, ModelKey<List<ItemKey>> watchers, ModelKey<Integer> watching,
    Function<ItemWrapper, ItemKey> meGetter)
  {
    super("Watch", Icons.WATCH_ACTION, me, watching, watchers, meGetter, "changeMeWatching");
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Change you watching selected " + Terms.ref_artifact + " state");
  }

  protected void customUpdate(final UpdateContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = ItemActionUtils.updateForEditMultipleItems(context);
    if (wrappers == null || wrappers.isEmpty()) {
      return;
    }
    updateNameAndToggled(context, wrappers, "Watch", "Stop Watching",
      "Watching state is different for selected $(app.term.artifacts)");
    IdActionProxy.setShortcut(context, MetaInfo.WATCH_ARTIFACT);
  }
}