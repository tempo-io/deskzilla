package com.almworks.util;

import java.util.Arrays;
import java.util.Random;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ArraySeekPerfs {
  private static final int MAX_N = 40;
  private static final int DATA_ATTEMPTS = 20;

  public static void main(String[] args) {
    new ArraySeekPerfs().run();
  }

  private final Random random = new Random(time() ^ ((time() * 53) << 32));

  public final void binarySearch(long[] array, long sought, int count) {
    for (int i = 0; i < count; i++) {
      Arrays.binarySearch(array, sought);
    }
  }

  public final void orderSearch(long[] array, long sought, int count) {
    for (int i = 0; i < count; i++) {
      int len = array.length;
      for (int j = 0; j < len; j++)
        if (array[j] == sought)
          break;
    }
  }

  private long[] createArray(int length) {
    long[] arr = new long[length];
    for (int i = 0; i < arr.length; i++)
      arr[i] = random.nextLong();
    Arrays.sort(arr);
    return arr;
  }

  private void run() {
    System.out.print("Calibrating...");
    int repeat = calibrate(MAX_N);
    System.out.println(" using count " + repeat);

    for (int n = 1; n < MAX_N; n++) {
      long times[] = {0, 0, 0, 0};
      for (int k = 0; k < DATA_ATTEMPTS; k++) {
        long[] array = createArray(n);
        long notFound = getNotFoundValue(array);
        long found = getFoundValue(array);

        long time;

        time = time();
        binarySearch(array, found, repeat);
        times[0] += time() - time;

        time = time();
        orderSearch(array, found, repeat);
        times[1] += time() - time;

        time = time();
        binarySearch(array, notFound, repeat);
        times[2] += time() - time;

        time = time();
        orderSearch(array, notFound, repeat);
        times[3] += time() - time;
      }
      System.out.println(n + ": bin.f=" + times[0] + "; ord.f=" + times[1] + "; bin.n=" + times[2] + "; ord.n=" + times[3]);
    }
  }

  private long getFoundValue(long[] array) {
    return array[random.nextInt(array.length)];
  }

  private int calibrate(int length) {
    long time = time();
    long[] array = createArray(length);
    long sought = getNotFoundValue(array);
    final int INCREMENT = 1000;
    final int WANTED_TIME = 200;
    final int CALIBRATE_STEPS = 8;
    int count = 0;
    while (true) {
      orderSearch(array, sought, INCREMENT);
      count += INCREMENT;
      long timePassed = time() - time;
      if (timePassed >= WANTED_TIME * CALIBRATE_STEPS)
        break;
    }
    return count / CALIBRATE_STEPS;
  }

  private long getNotFoundValue(long[] array) {
    long val = 0;
    int len = array.length;
    while (true) {
      int i;
      for (i = 0; i < len; i++) {
        if (val == array[i])
          break;
      }
      if (i == len)
        return val;
      val++;
    }
  }

  private static long time() {
    return System.currentTimeMillis();
  }
}
