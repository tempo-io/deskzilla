package com.almworks.util.components;

import com.almworks.util.files.ExternalBrowser;
import com.almworks.util.ui.actions.*;

public class OpenBrowserAnAction extends SimpleAction {
  private final String myUrl;
  private final boolean myEncoded;

  public OpenBrowserAnAction(String url, boolean encoded, String text) {
    super(text);
    myUrl = url;
    myEncoded = encoded;
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {}

  protected void doPerform(ActionContext context) throws CantPerformException {
    ExternalBrowser.openURL(myUrl, myEncoded);
  }
}
