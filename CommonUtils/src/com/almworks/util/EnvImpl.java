package com.almworks.util;

import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Map;

public interface EnvImpl {
  @Nullable
  String getProperty(@NotNull String key);

  @NotNull
  Collection<String> getPropertyKeys();

  void changeProperties(@NotNull Map<String, String> diff);
}
