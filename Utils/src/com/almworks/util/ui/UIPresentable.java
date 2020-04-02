package com.almworks.util.ui;

import com.almworks.util.config.Configuration;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
public interface UIPresentable {
  @Nullable
  UIComponentWrapper createUIWrapper(Configuration configuration);
}
