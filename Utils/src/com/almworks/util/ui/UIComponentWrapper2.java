package com.almworks.util.ui;

import org.almworks.util.detach.Detach;

public interface UIComponentWrapper2 extends UIComponentWrapper {
  @Deprecated
  void dispose();

  Detach getDetach();
}
