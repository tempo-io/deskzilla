package com.almworks.bugzilla.integration.oper.js;

public class JSParserDelegator implements JSParserVisitor {
  private static final JSParserDelegate EMPTY = new JSParserAdapter() {};
  private JSParserDelegate myDelegate;

  public JSParserDelegator(JSParserDelegate delegate) {
    setDelegate(delegate);
  }

  public JSParserDelegator() {
    this(null);
  }

  public void visitStatementStart() {
    myDelegate.visitStatementStart();
  }

  public void visitStatementEnd() {
    myDelegate.visitStatementEnd();
  }

  public void visitBlockStart(int blockLevel) {
    myDelegate.visitBlockStart(blockLevel);
  }

  public void visitBlockEnd(int blockLevel) {
    myDelegate.visitBlockEnd(blockLevel);
  }

  public void visitStringLiteral(String literal) {
    myDelegate.visitStringLiteral(literal);
  }

  public void visitNumberSequence(String numbers) {
    myDelegate.visitNumberSequence(numbers);
  }

  public void visitIdentifier(String identifier) {
    myDelegate.visitIdentifier(identifier);
  }

  public void visitSpecialChar(char c) {
    myDelegate.visitSpecialChar(c);
  }

  public void visitStart() {
    myDelegate.visitStart();
  }

  public void visitFinish(boolean success) {
    myDelegate.visitFinish(success);
  }

  public void setDelegate(JSParserDelegate delegate) {
    if (myDelegate != null)
      myDelegate.onUndelegated();
    myDelegate = delegate == null ? EMPTY : delegate;
    myDelegate.onDelegated();
  }
}
