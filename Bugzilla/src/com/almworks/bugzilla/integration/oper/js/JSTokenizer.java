package com.almworks.bugzilla.integration.oper.js;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.ParseException;

/**
 * Limited-function tokenizer of JavaScript
 */
public class JSTokenizer {
  private final char[] mySource;
  public static final Charset ASCII = Charset.forName("ASCII");

  public JSTokenizer(String source) {
    mySource = source.toCharArray();
  }

  public void visit(JSTokenizerVisitor visitor) throws ParseException {
    boolean success = false;
    visitor.visitStart();
    try {

      int p = 0;
      int length = mySource.length;

      boolean inBlockComment = false;
      boolean inLineComment = false;
      boolean inStringLiteral = false;
      boolean inNumberSequence = false;
      boolean inIdentifier = false;

      StringBuffer bufIdentifier = new StringBuffer();
      StringBuffer bufNumberSequence = new StringBuffer();
      StringBuffer bufStringLiteral = new StringBuffer();
      char lastStringBound = 0;

      while (p < length) {
        char c = mySource[p++];

        if (inBlockComment) {
          if (c == '*' && p < length && mySource[p] == '/') {
            p++;
            inBlockComment = false;
          } else {
            // skip comment
          }
          continue;
        }

        if (inLineComment) {
          if (isEOL(c)) {
            inLineComment = false;
//            visitor.visitEOL();
          }
          continue;
        }

        if (inStringLiteral) {
          if (c == lastStringBound) {
            visitor.visitStringLiteral(bufStringLiteral.toString());
            bufStringLiteral.setLength(0);
            inStringLiteral = false;
          } else if (c == '\\') {
            // escape sequences
            if (p >= length)
              throw parseException(p);
            c = mySource[p++];
            if (c == '\\' || c == '\'' || c == '\"' || c == '/') {
              bufStringLiteral.append(c);
            } else if (c == 'x') {
              if (p + 1 >= length)
                throw parseException(p);
              char cc = decodeChar(p);
              p += 2;
              bufStringLiteral.append(cc);
            } else if (c == 'r' || c == 'n') {
              bufStringLiteral.append(c == 'r' ? '\r' : '\n');
            } else if (c == '\r' || c == '\n') {
              if (p + 1 < length && (mySource[p + 1] == '\r' || mySource[p + 1] == '\n') && c != mySource[p + 1])
                p++;
              // this is ok, line break
            } else {
              assert false : c; // don't know about this symbol
              bufStringLiteral.append(c);
            }
          } else {
            bufStringLiteral.append(c);
          }
          continue;
        }


        if (inNumberSequence) {
          // don't support numeric literals
          if (Character.isDigit(c)) {
            bufNumberSequence.append(c);
            continue;
          } else {
            visitor.visitNumberSequence(bufNumberSequence.toString());
            bufNumberSequence.setLength(0);
            inNumberSequence = false;
            // fall through with c
          }
        }


        if (inIdentifier) {
          if (isIdentifierChar(c)) {
            bufIdentifier.append(c);
            continue;
          } else {
            visitor.visitIdentifier(bufIdentifier.toString());
            bufIdentifier.setLength(0);
            inIdentifier = false;
            // fall through with c
          }
        }

        if (isEOL(c)) {
//          visitor.visitEOL();
          // coalesce all eols
          while (p < length && isEOL(mySource[p]))
            p++;
          continue;
        }

        if (Character.isWhitespace(c))
          continue;

        if (isIdentifierFirstChar(c)) {
          bufIdentifier.setLength(0);
          bufIdentifier.append(c);
          inIdentifier = true;
          continue;
        }

        if (Character.isDigit(c)) {
          bufNumberSequence.setLength(0);
          bufNumberSequence.append(c);
          inNumberSequence = true;
          continue;
        }

        if (c == '\'' || c == '\"') {
          bufStringLiteral.setLength(0);
          lastStringBound = c;
          inStringLiteral = true;
          continue;
        }

        if (c == '/' && p < length) {
          char c2 = mySource[p];
          if (c2 == '/') {
            p++;
            inLineComment = true;
            continue;
          } else if (c2 == '*') {
            p++;
            inBlockComment = true;
            continue;
          }
          // fall through
        }

        visitor.visitSpecialChar(c);
      }

      if (inBlockComment)
        throw parseException(p);
      if (inStringLiteral)
        throw parseException(p);
      if (inIdentifier)
        visitor.visitIdentifier(bufIdentifier.toString());
      if (inNumberSequence)
        visitor.visitNumberSequence(bufNumberSequence.toString());

      success = true;
    } finally {
      try {
        visitor.visitFinish(success);
      } catch (Exception e) {
        // ignore
      }
    }
  }

  private boolean isIdentifierFirstChar(char c) {
    return Character.isLetter(c) || c == '_' || c == '$';
  }

  private boolean isIdentifierChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  private char decodeChar(int p) throws ParseException {
    int d1 = decodeHexDigit(mySource[p]);
    int d2 = decodeHexDigit(mySource[p + 1]);
    if (d1 < 0 || d2 < 0)
      throw parseException(p);
    int n = d1 * 16 + d2;
    CharBuffer charBuffer = ASCII.decode(ByteBuffer.wrap(new byte[]{(byte) n}));
    assert charBuffer.position() == 0;
    if (charBuffer.remaining() == 0)
      throw parseException(p);
    char cc = charBuffer.charAt(0);
    return cc;
  }

  private ParseException parseException(int p) {
    return new ParseException(new String(mySource), p);
  }

  private boolean isEOL(char c) {
    return c == '\n' || c == '\r';
  }

  private int decodeHexDigit(char c) {
    if(c >= '0' && c <= '9') {
      return c - '0';
    }
    if(c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    if(c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    return -1;
  }
}
