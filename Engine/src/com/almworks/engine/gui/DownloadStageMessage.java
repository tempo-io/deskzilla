package com.almworks.engine.gui;

import com.almworks.api.application.*;
import com.almworks.api.gui.MainMenu;
import com.almworks.util.Terms;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.ActionRegistry;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;

class DownloadStageMessage implements ChangeListener {
  private static final String SHORT_MESSAGE = Local.parse("Some " + Terms.ref_artifact + " details are not downloaded yet");
  private static final TypedKey<Object> MESSAGE_KEY = TypedKey.create("downloadStage");
  private final ModelMap myModel;
  private final ItemMessages myMessages;

  public DownloadStageMessage(ModelMap model, ItemMessages messages) {
    myModel = model;
    myMessages = messages;
  }

  public void attach(Lifespan life) {
    myModel.addAWTChangeListener(life, this);
    updateMessage();
  }

  public void onChange() {
    updateMessage();
  }

  private void updateMessage() {
    ItemDownloadStage stage = ItemDownloadStageKey.retrieveValue(myModel);
    if (stage != ItemDownloadStage.QUICK) myMessages.setMessage(MESSAGE_KEY, null);
    else {
      LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(myModel);
      String longDescription;
      AnAction[] actions;
      if (lis != null) {
        longDescription = lis.getMetaInfo().getPartialDownloadHtml();
        actions = new AnAction[]{lis.getActor(ActionRegistry.ROLE).getAction(MainMenu.Edit.DOWNLOAD)};
      } else {
        longDescription = null;
        actions = AnAction.EMPTY_ARRAY;
      }
      myMessages.setMessage(MESSAGE_KEY, ItemMessage.information(Icons.ATTENTION, SHORT_MESSAGE, longDescription, actions));
    }
  }
}
