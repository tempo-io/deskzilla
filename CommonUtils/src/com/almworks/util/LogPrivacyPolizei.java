package com.almworks.util;

import org.jetbrains.annotations.*;

public interface LogPrivacyPolizei {
  @NotNull String examine(@NotNull String messageToBeLogged);
}
