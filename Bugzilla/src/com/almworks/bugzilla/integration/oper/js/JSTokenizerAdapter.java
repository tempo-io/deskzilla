package com.almworks.bugzilla.integration.oper.js;

import org.almworks.util.TypedKey;

public abstract class JSTokenizerAdapter implements JSTokenizerVisitor {
  public void visitStringLiteral(String literal) {
    visitToken(TokenType.STRING, literal);
  }

  public void visitNumberSequence(String numbers) {
    visitToken(TokenType.NUMBER, numbers);
  }

  public void visitIdentifier(String identifier) {
    visitToken(TokenType.IDENTIFIER, identifier);
  }

  public void visitSpecialChar(char c) {
    visitToken(TokenType.SPECIAL, Character.valueOf(c));
  }

  public void visitStart() {
    visitToken(TokenType.START, null);
  }

  public void visitFinish(boolean success) {
    visitToken(TokenType.FINISH, Boolean.valueOf(success));
  }

  protected <T> void visitToken(TokenType<T> type, T value) {
  }

  public static final class TokenType<T> extends TypedKey<T> {
    public static final TokenType<String> STRING = type("STRING");
    public static final TokenType<String> NUMBER = type("NUMBER");
    public static final TokenType<String> IDENTIFIER = type("IDENTIFIER");
    public static final TokenType<Character> SPECIAL = type("SPECIAL");
    public static final TokenType<Void> START = type("START");
    public static final TokenType<Boolean> FINISH = type("FINISH");

    private TokenType(String name) {
      super(name, null, null);
    }

    private static <T> TokenType<T> type(String name) {
      return new TokenType<T>(name);
    }
  }

}
