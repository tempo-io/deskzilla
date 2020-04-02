package com.almworks.util;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Random;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WeakRefPerfs {
  private final Random myRandom;
  private static final int MAX_N = 1000;

  public static void main(String[] args) {
    new WeakRefPerfs().run();
  }

  public WeakRefPerfs() {
    myRandom = new Random(System.currentTimeMillis());
  }

  private void run() {
    X[] strongs = new X[100000];
    Reference[] weaks = new Reference[strongs.length];
    for (int i = 0; i < strongs.length; i++) {
      strongs[i] = new XImpl(myRandom.nextLong());
      weaks[i] = new SoftReference<X>(strongs[i]);
    }

    long time = time();
    long r = 0;
    for (int n = 0; n < MAX_N; n++)
      for (int i = 0; i < strongs.length; i++)
        r += strongs[i].getX();
    long dif = time() - time;
    System.out.println("strong: " + dif);

    time = time();
    r = 0;
    for (int n = 0; n < MAX_N; n++)
      for (int i = 0; i < weaks.length; i++)
        r += ((X)weaks[i].get()).getX();
    dif = time() - time;
    System.out.println("weak: " + dif);
  }

  private static long time() {
    return System.currentTimeMillis();
  }

  private interface X {
    public long getX();
  }

  private class XImpl implements X {
    private final long myX;

    public XImpl(long x) {
      myX = x;
    }

    public long getX() {
      return myX;
    }
  }
}
