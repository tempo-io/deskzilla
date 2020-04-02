package com.almworks.util;

import org.jetbrains.annotations.*;

public class Break extends Exception {
  public Break(String message) {
    super(message);
  }

  public Break() {}

  private static <T> T breakImpl(boolean condition, T goodResult, String msg, Object... args) throws Break {
    if(condition) {
      throw new Break(String.format(msg, args));
    }
    return goodResult;
  }

  @Nullable(documentation = "returns null if condition is true")
  public static <T> T breakIf(boolean condition, String msg, Object... args) throws Break {
    return breakImpl(condition, (T)null, msg, args);
  }

  @NotNull(documentation = "always throws, never returns")
  public static <T> T breakHere(String msg, Object... args) throws Break {
    return breakImpl(true, (T)null, msg, args);
  }

  @NotNull(documentation = "throws if null, returns t otherwise")
  public static <T> T breakIfNull(T t, String msg, Object... args) throws Break {
    return breakImpl(t == null, t, msg, args);
  }
}
