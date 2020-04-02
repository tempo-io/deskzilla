package com.almworks.bugzilla.integration.oper.js;

import java.text.ParseException;

public class JSParser {
  private final JSTokenizer myTokenizer;

  public JSParser(String js) {
    myTokenizer = new JSTokenizer(js);
  }

  public void visit(final JSParserVisitor visitor) throws ParseException {
    myTokenizer.visit(new MyWrapper(visitor));
  }

  private static class MyWrapper implements JSTokenizerVisitor {
    private final JSParserVisitor myVisitor;
    boolean myInStatement = false;
    private int myBlockLevel = 0;

    public MyWrapper(JSParserVisitor visitor) {
      myVisitor = visitor;
    }

    public void visitStringLiteral(String literal) {
      startStatement();
      myVisitor.visitStringLiteral(literal);
    }

    public void visitNumberSequence(String numbers) {
      startStatement();
      myVisitor.visitNumberSequence(numbers);
    }

    public void visitIdentifier(String identifier) {
      startStatement();
      myVisitor.visitIdentifier(identifier);
    }

    public void visitSpecialChar(char c) {
      if (c != ';')
        startStatement();
      myVisitor.visitSpecialChar(c);
      if (c == '{')
        increaseBlockLevel();
      else if (c == '}')
        decreaseBraceLevel();
      else if (c == ';')
        endStatement();
    }

    public void visitStart() {
      myVisitor.visitStart();
    }

    public void visitFinish(boolean success) {
      if (success)
        endStatement();
      myVisitor.visitFinish(success);
    }

    private void decreaseBraceLevel() {
      myVisitor.visitBlockEnd(myBlockLevel);
      myBlockLevel--;
      assert myBlockLevel >= 0;
    }

    private void endStatement() {
      if (!myInStatement)
        return;
      myVisitor.visitStatementEnd();
      myInStatement = false;
    }

    private void increaseBlockLevel() {
      myBlockLevel++;
      myVisitor.visitBlockStart(myBlockLevel);
    }

    private void startStatement() {
      if (myInStatement)
        return;
      myVisitor.visitStatementStart();
      myInStatement = true;
    }
  }
}
