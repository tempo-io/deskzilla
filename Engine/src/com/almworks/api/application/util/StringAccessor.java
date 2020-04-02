package com.almworks.api.application.util;

import org.jetbrains.annotations.*;

public class StringAccessor extends BaseModelKey.SimpleDataAccessor<String> {
  public StringAccessor(String id) {
    super(id);
  }

  @Override
    protected Object getCanonicalValueForComparison(@Nullable String value) {
    return value == null || value.isEmpty() ? null : value;
  }
}
