package com.almworks.bugzilla.integration.oper.js;

public abstract class JSParserAdapter extends JSTokenizerAdapter implements JSParserDelegate {
  public void onDelegated() {
  }

  public void onUndelegated() {
  }

  public void visitStatementStart() {
  }

  public void visitStatementEnd() {
  }

  public void visitBlockStart(int blockLevel) {
    visitBlockLevel(blockLevel);
  }
  public void visitBlockEnd(int blockLevel) {
    visitBlockLevel(blockLevel - 1);
  }

  protected void visitBlockLevel(int level) {
  }
}
