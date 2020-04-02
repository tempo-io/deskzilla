package com.almworks.bugzilla.integration.oper.js;

public interface JSParserVisitor extends JSTokenizerVisitor {
  void visitBlockEnd(int blockLevel);

  void visitBlockStart(int blockLevel);

  void visitStatementEnd();

  void visitStatementStart();
}
