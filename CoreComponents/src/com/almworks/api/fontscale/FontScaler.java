package com.almworks.api.fontscale;

import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

import javax.swing.*;

public interface FontScaler {
  Role<FontScaler> ROLE = Role.role(FontScaler.class);

  void installScaleRoot(@NotNull JComponent component, @Nullable String configClass);
}
