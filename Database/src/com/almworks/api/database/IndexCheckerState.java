package com.almworks.api.database;

import com.almworks.util.model.ScalarModel;

public interface IndexCheckerState {
  ScalarModel<String> getState();
}
