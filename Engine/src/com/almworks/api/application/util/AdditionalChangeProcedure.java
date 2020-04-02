package com.almworks.api.application.util;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.UserChanges;

public interface AdditionalChangeProcedure {
  public void addChanges(UserChanges changes, ModelKey<?> modelKey);
}
