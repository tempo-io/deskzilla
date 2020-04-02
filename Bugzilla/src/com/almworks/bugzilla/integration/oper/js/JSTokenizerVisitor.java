package com.almworks.bugzilla.integration.oper.js;

public interface JSTokenizerVisitor {
  void visitStringLiteral(String literal);

  void visitNumberSequence(String numbers);

  void visitIdentifier(String identifier);

  void visitSpecialChar(char c);

  void visitStart();

  void visitFinish(boolean success);
}
