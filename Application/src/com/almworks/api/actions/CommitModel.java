package com.almworks.api.actions;

import com.almworks.api.gui.DialogManager;
import com.almworks.edit.ItemEditUtil;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.EditDrain;

import java.util.Collection;

public class CommitModel extends EditCommit.Adapter {
  private final ItemUiModelImpl[] myModels;
  private final DialogManager myDialogMan;

  public CommitModel(ItemUiModelImpl[] model, DialogManager dialogMan) {
    myModels = model;
    myDialogMan = dialogMan;
  }

  @Override
  public void performCommit(EditDrain drain) throws DBOperationCancelledException {
    for (ItemUiModelImpl model : myModels) {
      ItemEditUtil.addModelChanges(drain.changeItem(model.getItem()), model, myDialogMan);
    }
  }

  /**
   * @param dialogMan used to show error message if commit fails
   */
  public static EditCommit create(ItemUiModelImpl model, DialogManager dialogMan) {
    return new CommitModel(new ItemUiModelImpl[]{model}, dialogMan);
  }

  /**
   * @param dialogMan used to show error message if commit fails
   */
  public static EditCommit create(Collection<? extends ItemUiModelImpl> models, DialogManager dialogMan) {
    if (models == null || models.isEmpty()) return DEAF;
    return new CommitModel(models.toArray(new ItemUiModelImpl[models.size()]), dialogMan);
  }
}
