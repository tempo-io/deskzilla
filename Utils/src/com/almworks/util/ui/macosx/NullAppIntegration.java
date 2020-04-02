package com.almworks.util.ui.macosx;

import com.almworks.util.commons.Procedure;

import javax.swing.*;
import java.net.URI;

/**
 * Null object pattern.
 */
public class NullAppIntegration extends MacAppIntegration {
  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public void installMacHandlers() {}

  @Override
  public void setQuitHandler(Runnable quitHandler) {}

  @Override
  public void setAboutHandler(Runnable quitHandler) {}

  @Override
  public void setReopenHandler(Runnable quitHandler) {}

  @Override
  public void setOpenUriHandler(Procedure<URI> uriHandler) {}

  @Override
  public void setDefaultMenuBar(JMenuBar menuBar) {}
}
