package com.almworks.api.explorer.util;

import com.almworks.util.collections.Convertor;

public interface VariantsConfigurator<T> {
  void configure(ConnectContext context, VariantsAcceptor<T> acceptor);

  Convertor<T, String> getIdentityConvertor();
}
