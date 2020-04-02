/*
 * $Header$
 * $Revision: 5069 $
 * $Date: 2010-03-15 16:32:42 +0300 (Mon, 15 Mar 2010) $
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.commons.httpclient.server;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Pumps data between a pair of input / output streams. Used to
 * connect the two ends (left and right) of a bidirectional communication channel.
 * Instances of this class are thread safe.
 *
 * @author Ortwin Glueck
 */
class BidiStreamProxy {
  private StreamProxy leftToRight, rightToLeft;
  private int state = 0;

  /**
   * Sets up a new connection between two peers (left and right)
   *
   * @param leftIn   input channel of the left peer
   * @param leftOut  output channel of the left peer
   * @param rightIn  input channel of the right peer
   * @param rightOut output channel of the right peer
   */
  public BidiStreamProxy(InputStream leftIn, OutputStream leftOut, InputStream rightIn, OutputStream rightOut) {
    leftToRight = new StreamProxy(leftIn, rightOut);
    rightToLeft = new StreamProxy(rightIn, leftOut);
  }

  /**
   * Starts pumping the information from left to right and vice versa.
   * This is performed asynchronously so this method returns immediately.
   */
  public synchronized void start() {
    if (state != 0) throw new IllegalStateException("Cannot start twice");
    leftToRight.start();
    rightToLeft.start();
    state = 1;
  }

  /**
   * Aborts the communication between the peers and releases all resources.
   * Note: The method does not wait for the pump threads to terminate.
   */
  public synchronized void abort() {
    if (leftToRight != null) leftToRight.abort();
    if (rightToLeft != null) rightToLeft.abort();
    leftToRight = null;
    rightToLeft = null;
  }

  /**
   * Blocks until all data has been copied. Basically calls the
   * join method on the pump thread.
   *
   * @throws InterruptedException
   */
  public void block() throws InterruptedException {
    if (state != 1) throw new IllegalStateException("Cannot block before started");
    leftToRight.block();
    rightToLeft.block();
  }
}
