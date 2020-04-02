package com.almworks.bugzilla.provider.meta;

import com.almworks.actions.RemovePrimaryItemAction;
import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.util.ValueKey;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.BugzillaBook;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.i18n.LText1;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.DialogsUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Util;

import java.util.Collections;

class RemoveBugForSyncProblemAction extends SimpleAction {
  private static final String X = "Bugzilla.Metainfo.Actions.";
  private static final LText1<Integer> BUG_REMOVE_CONFIRMATION = BugzillaBook.text(X + "bug-remove-confirmation",
    "Are you sure you want to remove {0,choice,0#the selected bug|0<bug '#'{0,number, integer}}?", 0);

  private final ValueKey<Integer> myId;
  private final Synchronizer mySynchronizer;

  public RemoveBugForSyncProblemAction(ValueKey<Integer> id, Engine engine) {
    super((String)null, Icons.ACTION_REMOVE_ARTIFACT);
    setDefaultText(PresentationKey.NAME, Local.parse("Remove " + Terms.ref_Artifact));
    myId = id;
    setDefaultText(PresentationKey.SHORT_DESCRIPTION,
      L.tooltip("Remove selected erroneous " + Terms.ref_artifact + " from the local database"));
    mySynchronizer = engine.getSynchronizer();
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    ItemActionUtils.basicUpdate(context);
    Engine engine = context.getSourceObject(Engine.ROLE);
    // todo #822 (http://bugzilla/main/show_bug.cgi?id=822)
    final long item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER).getItem();
    boolean isEnabled = false;
    for (ItemSyncProblem problem : engine.getSynchronizer().getItemProblems(item)) {
      if (problem.isCauseForRemoval()) {
        isEnabled = true;
        break;
      }
    }
    context.setEnabled(isEnabled ? EnableState.ENABLED : EnableState.INVISIBLE);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    ItemWrapper itemWrapper = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    PropertyMap lastDBValues = itemWrapper.getLastDBValues();
    Integer bugId = Util.NN(myId.getValue(lastDBValues), -1);
    int r = DialogsUtil.askUser(context.getComponent(), BUG_REMOVE_CONFIRMATION.format(bugId),
      L.dialog(Local.parse("Remove " + Terms.ref_Artifact)), DialogsUtil.YES_NO_OPTION);
    if (DialogsUtil.YES_OPTION == r) {
      RemovePrimaryItemAction.doRemoveItems(context, Collections.singleton(itemWrapper), mySynchronizer);
    }
  }
}
