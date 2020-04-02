package com.almworks.api.gui;

import com.almworks.util.collections.Containers;
import org.almworks.util.Util;

/**
 * This class provides build number information and compares build numbers.
 * Build number is composed from major and minor number, minor number could be omitted.
 */
public class BuildNumber implements Comparable<BuildNumber> {
  private final int myMajor;
  private final int myMinor;
  private final String myDisplayable;

  public BuildNumber(String string) {
    assert string != null : getClass() + ":" + string;
    int build;
    try {
      build = Integer.parseInt(string);
    } catch (NumberFormatException e) {
//      Log.warn("BuildNumber: cannot understand " + string);
      myDisplayable = Util.NN(string);
      myMajor = 0;
      myMinor = 0;
      return;
    }
    if (build > 100000) {
      myMajor = build / 1000;
      myMinor = build % 1000;
    } else {
      myMajor = build;
      myMinor = 0;
    }
    myDisplayable = myMinor <= 0 ? Integer.toString(myMajor) : myMajor + "." + myMinor;
  }

  /**
   * @return string that could be displayed, like "844.3"
   */
  public String toDisplayableString() {
    return myDisplayable;
  }

  /**
   * @return major build number that tells where does this product come from on the head branch. Returns 0 if
   *         build information is unavailable or broken.
   */
  public int getMajor() {
    return myMajor;
  }

  /**
   * @return minor number that is kind of "patch level" for a certain major branch. returns 0 if no minor number is there.
   */
  public int getMinor() {
    return myMinor;
  }

  public int compareTo(BuildNumber that) {
    int thisMajor = getMajor();
    int thatMajor = that.getMajor();
    if (thisMajor <= 0 || thatMajor <= 0)
      return toDisplayableString().compareTo(that.toDisplayableString());
    if (thisMajor != thatMajor)
      return Containers.compareInts(thisMajor, thatMajor);
    return Containers.compareInts(getMinor(), that.getMinor());
  }

  public String toString() {
    return myDisplayable;
  }

  public int hashCode() {
    return myDisplayable.hashCode();
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof BuildNumber))
      return false;
    return Util.equals(myDisplayable, ((BuildNumber) obj).myDisplayable);
  }

  public int getCompositeInteger() {
    return myMinor == 0 ? myMajor : myMajor * 1000 + myMinor;
  }
}
