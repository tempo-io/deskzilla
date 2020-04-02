package com.almworks.container;

import com.almworks.api.container.ContainerPath;
import org.jetbrains.annotations.*;
import org.picocontainer.PicoContainer;

interface ComponentContainerParent {
  @NotNull PicoContainer getPico();

  @Nullable Selectors getSelectors();

  @Nullable ContainerPath getPath();

  @Nullable EventRouterImpl getEventRouter();
}
