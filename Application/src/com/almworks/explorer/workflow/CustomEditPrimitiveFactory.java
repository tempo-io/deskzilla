package com.almworks.explorer.workflow;

import com.almworks.util.config.ReadonlyConfiguration;
import org.jetbrains.annotations.*;

public interface CustomEditPrimitiveFactory {
  @Nullable(documentation = "if the factory does not know such edit primitive")
  LoadedEditPrimitive<?> createPrimitive(ReadonlyConfiguration config, String attrName);
}
