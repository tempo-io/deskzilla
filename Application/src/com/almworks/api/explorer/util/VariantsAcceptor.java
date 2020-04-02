package com.almworks.api.explorer.util;

import com.almworks.util.advmodel.AListModel;
import com.almworks.util.config.Configuration;
import org.jetbrains.annotations.*;

public interface VariantsAcceptor<T> {
  void accept(AListModel<T> variants, @Nullable Configuration recentConfig);
}
