package com.almworks.api.misc;

import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.Bottleneck;
import util.concurrent.SynchronizedBoolean;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Displays Swing timer's inability to catch up with the change of system time backwards.<br>
 * Run either {@link #testRepeatedTimer} or {@link #testBottleneck}. Each of them prints current system time once in a second.
 * <ol><li>Change system time forward; observe: the messages show the new system time.</li>
 * <li>Change system time backward (I tried setting date = yesterday in Windows 7). Observe: no more messages.</li></ol>
 * Note that {@link TimeService} doesn't have this pitfall.
 * */
class BackwardTimeTest {
  public static final SimpleDateFormat format = new SimpleDateFormat("d.M H:m:s");
  private static final ActionListener timePrinter = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      long now = System.currentTimeMillis();
      System.out.println("Update: " + format.format(new Date(now)));
    }
  };

  public static void main(String[] args) throws Exception {
//    testRepeatedTimer();
    testBottleneck();
    waitForever();
  }

  private static void waitForever() throws Exception {
    Object o = new Object();
    synchronized (o) {
      o.wait();
    }
  }

  private static void testRepeatedTimer() {
    Timer timer = new Timer(1000, timePrinter);
    timer.setRepeats(true);
    timer.setCoalesce(true);
    timer.start();
  }

  private static void testBottleneck() throws Exception {
    final SynchronizedBoolean requested = new SynchronizedBoolean(false);
    Bottleneck bottleneck = new Bottleneck(1000, ThreadGate.AWT, new Runnable() {
      @Override
      public void run() {
        timePrinter.actionPerformed(null);
        requested.set(true);
      }
    });

    while (true) {
      bottleneck.requestDelayed();
      requested.waitForValue(true);
      requested.set(false);
    }
  }
}
