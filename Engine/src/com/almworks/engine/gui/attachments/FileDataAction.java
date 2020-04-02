package com.almworks.engine.gui.attachments;

import com.almworks.util.ui.actions.*;

public abstract class FileDataAction extends SimpleAction {
  public FileDataAction(String name) {
    super(name);
    watchRole(FileData.FILE_DATA);
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    context.getSourceObject(FileData.FILE_DATA);
  }

  protected void doPerform(ActionContext context) throws CantPerformException {
    FileData data = context.getSourceObject(FileData.FILE_DATA);
    perform(data);
  }

  protected abstract void perform(FileData data);
}
