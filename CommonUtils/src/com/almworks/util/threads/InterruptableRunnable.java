package com.almworks.util.threads;

/**
 * @author dyoma
 */
public interface InterruptableRunnable {
  void run() throws InterruptedException;
}
