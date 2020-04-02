package com.almworks.api.explorer.rules;

import com.almworks.api.application.ItemWrapper;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.commons.Function;
import com.almworks.util.ui.actions.*;

import java.util.Map;

/**
 * @author dyoma
 */
public class EditorAutoAssignAction extends BaseAutoAssignAction {
  public EditorAutoAssignAction(Map<PresentationKey, Object> presentation, PresentationKey<String> hintKey, Function<ItemWrapper, Edit> getAssign) {
    super(presentation, hintKey, getAssign);
  }

  public void update(UpdateContext context) throws CantPerformException {
    for (ItemUiModelImpl model : context.getSourceCollection(ItemUiModelImpl.ROLE))
      context.updateOnChange(model);
    ApplicableRules<ItemUiModelImpl> applicable = basicUpdate(context, ItemUiModelImpl.ROLE);
    finishUpdate(context, applicable);
  }

  public void perform(ActionContext context) throws CantPerformException {
    ApplicableRules<ItemUiModelImpl> applicable = collectApplicable(context, ItemUiModelImpl.ROLE);
    if (applicable.getCount() == 0) {
      assert false;
      return;
    }
    for (int i = 0; i < applicable.getCount(); i++)
      applicable.applyEditOnForm(i, applicable.getItem(i));
  }
}
