package com.almworks.api.explorer.rules;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.UiItem;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Function;
import com.almworks.util.ui.actions.*;

import java.util.Map;

/**
 * @author dyoma
 */
public class AutoAssignAction extends BaseAutoAssignAction {
  public AutoAssignAction(Map<PresentationKey, Object> presentation, PresentationKey<String> hintKey, Function<ItemWrapper, Edit> getAssign) {
    super(presentation, hintKey, getAssign);
  }

  public void update(UpdateContext context) throws CantPerformException {
    ApplicableRules<ItemWrapper> applicable = basicUpdate(context, ItemWrapper.ITEM_WRAPPER);
    context.watchModifiableRole(SyncManager.MODIFIABLE);
    ItemActionUtils.checkNotLocked(context, applicable.getItemsCollection());
    finishUpdate(context, applicable);
  }

  public void perform(final ActionContext context) throws CantPerformException {
    final ApplicableRules<ItemWrapper> rules = collectApplicable(context, ItemWrapper.ITEM_WRAPPER);
    CantPerformException.ensure(rules.getCount() > 0);

    LongList items = LongSet.collect(UiItem.GET_ITEM, rules.getItemsCollection());
    SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    boolean success = syncMan.commitEdit(items, new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        for(int i = 0; i < rules.getCount(); i++) {
          rules.applyEditDb(i, drain.changeItem(rules.getItem(i).getItem()));
        }
      }
    });
    CantPerformException.ensure(success);
  }
}
