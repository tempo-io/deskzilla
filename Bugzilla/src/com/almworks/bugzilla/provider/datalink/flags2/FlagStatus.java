package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.util.collections.Convertor;

public enum FlagStatus {
  QUESTION('?', "?"),
  PLUS('+', "+"),
  MINUS('-', "\u2212"),
  UNKNOWN('\0', "X");

  private final char myChar;
  private final String myDisplayChar;

  FlagStatus(char aChar, String displayChar) {
    myChar = aChar;
    myDisplayChar = displayChar;
  }

  public static FlagStatus fromChar(char status) {
    for (FlagStatus f : values()) {
      if (f.myChar == status) return f;
    }
    if (status == 'X') return UNKNOWN;
    assert false : status;
    return UNKNOWN;
  }

  public static final Convertor<FlagStatus, String> TO_TEXT = new Convertor<FlagStatus, String>() {
    @Override
    public String convert(FlagStatus value) {
      return String.valueOf(value.myChar);
    }
  };

  public char getChar() {
    return myChar;
  }

  public String getDisplayPresentation() {
    if (this == MINUS) return "\u2212";
    if (this == UNKNOWN) return "";
    return String.valueOf(myChar);
  }


  @Override
  public String toString() {
    return String.valueOf(myChar);
  }

  public String getDisplayChar() {
    return myDisplayChar;
  }
}
