package com.almworks.bugzilla.integration.oper.js;

public interface JSParserDelegate extends JSParserVisitor {
  void onDelegated();

  void onUndelegated();
}
