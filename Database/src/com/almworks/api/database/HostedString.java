package com.almworks.api.database;

import com.almworks.util.collections.Convertor;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public abstract class HostedString implements Comparable<HostedString> {
  public static final HostedString EMPTY_STRING = new NotReallyHostedString("");
  public static final HostedString[] EMPTY_ARRAY = new HostedString[0];
  public static final Comparator<HostedString> COMPARATOR = new MyComparator();
  public static final Convertor<HostedString, String> TO_STRING = new MyToStringConvertor();
  public static final Convertor<String, HostedString> FROM_STRING = new MyFromStringConvertor();
  public static final CanvasRenderer<? super HostedString> CANVAS_RENDERER = new MyCanvasRenderer();

  // new to avoid interning
  protected static final String NULL_SENTINEL = new String("...null...");

  protected transient volatile String myCachedString;

  public static HostedString string(String string) {
    return new NotReallyHostedString(string);
  }

  @Nullable
  public String getFullString() {
    String cached = myCachedString;
    if (cached != null)
      return cached == NULL_SENTINEL ? null : cached;
    cached = loadString();
    myCachedString = cached == null ? NULL_SENTINEL : cached;
    return cached;
  }

  protected abstract String loadString();

  public boolean isEmpty() {
    String s = getFullString();
    return s == null || s.length() == 0;
  }

  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (!(obj instanceof HostedString))
      return false;
    return Util.equals(((HostedString) obj).getFullString(), getFullString());
  }

  public int hashCode() {
    String fullString = getFullString();
    return fullString == null ? 0 : fullString.hashCode();
  }

  public String toString() {
    return "HS[" + myCachedString + "]";
  }

  public int compareTo(HostedString that) {
    return COMPARATOR.compare(this, that);
  }

  /**
   * Returns not-null full string or empty string from the hosted string.
   */
  @NotNull
  public static String nn(HostedString string) {
    return string == null ? "" : Util.NN(string.getFullString());
  }
  

  private static class MyComparator implements Comparator<HostedString> {
    public int compare(HostedString o1, HostedString o2) {
      String s1 = Util.NN(o1 == null ? null : o1.getFullString());
      String s2 = Util.NN(o2 == null ? null : o2.getFullString());
      return s1.compareTo(s2);
    }
  }


  private static class MyToStringConvertor extends Convertor<HostedString, String> {
    public String convert(HostedString value) {
      return value == null ? null : value.getFullString();
    }
  }


  private static class MyFromStringConvertor extends Convertor<String, HostedString> {
    public HostedString convert(String value) {
      return new NotReallyHostedString(value);
    }
  }


  private static class MyCanvasRenderer implements CanvasRenderer<HostedString> {
    public void renderStateOn(CellState state, Canvas canvas, HostedString item) {
      if (item != null) {
        String string = item.getFullString();
        if (string != null) {
          canvas.appendText(string);
        }
      }
    }
  }
}
