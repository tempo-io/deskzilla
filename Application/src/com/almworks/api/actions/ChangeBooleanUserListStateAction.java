package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.api.gui.DialogManager;
import com.almworks.edit.ItemEditUtil;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Function;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

public abstract class ChangeBooleanUserListStateAction extends SimpleAction {
  private final String myKey;
  protected final ModelKey<Boolean> myMe;
  protected final Function<ItemWrapper, ItemKey> myMeGetter;
  protected final EditUserList myKeyEditor;

  protected ChangeBooleanUserListStateAction(@Nullable String name, @Nullable Icon icon, ModelKey<Boolean> me,
    ModelKey<Integer> count, ModelKey<List<ItemKey>> list, Function<ItemWrapper, ItemKey> meGetter,
    String key)
  {
    super(name, icon);
    myMe = me;
    myMeGetter = meGetter;
    myKeyEditor = new EditUserList(myMe, list, count);
    myKey = key;
    setDefaultPresentation(PresentationKey.TOGGLED_ON, false);
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.INVISIBLE);
    watchRole(ItemWrapper.ITEM_WRAPPER);
  }

  protected void doPerform(final ActionContext context) throws CantPerformException {
    final List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    AggregatingEditCommit commit = new AggregatingEditCommit();
    final DialogManager dialogMan = context.getSourceObject(DialogManager.ROLE);
    commit.addProcedure(null, new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        for (ItemWrapper wrapper : wrappers) {
          ItemUiModelImpl model = ItemActionUtils.getModel(wrapper);
          myKeyEditor.toggleMe(myMeGetter.invoke(wrapper), model.getModelMap());
          ItemVersionCreator creator = drain.changeItem(wrapper.getItem());
          ItemEditUtil.addModelChanges(creator, model, dialogMan);
        }
      }
    });
    SavedToLocalDBMessage.addTo(context, commit, myKey);
    final SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    boolean success = syncMan.commitEdit(LongSet.collect(ItemWrapper.GET_ITEM, wrappers), commit);
    CantPerformException.ensure(success);
  }

  protected void updateNameAndToggled(UpdateContext context, List<ItemWrapper> wrappers, String setActionName,
    String unsetActionName, String differentStateName)
  {
    Boolean voted = ItemActionUtils.getSameForAllKeyValue(myMe, wrappers);
    if (voted == null) {
      context.putPresentationProperty(PresentationKey.NAME, differentStateName);
      context.setEnabled(false);
    } else {
      context.setEnabled(true);
      context.putPresentationProperty(PresentationKey.TOGGLED_ON, voted);
      context.putPresentationProperty(PresentationKey.NAME, voted ? unsetActionName : setActionName);
    }
  }
}
