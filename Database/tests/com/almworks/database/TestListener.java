package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Failure;
import org.almworks.util.Util;

final class TestListener implements ArtifactListener {
  private final Artifact[] myPlaceHolder = new Artifact[4];
  private static final int ATTEMPTS = 10;
  private static final long DELAY = 100;

  private final int myAttempts;
  private final long myDelay = DELAY;
  private final String myName;

  public TestListener(long timeout) {
    this(timeout, null);
  }
  public TestListener(long timeout, String name) {
    myName = name;
    myAttempts = (int) ((timeout + myDelay - 1) / myDelay);
  }

  public TestListener() {
    myAttempts = ATTEMPTS;
    myName = null;
  }

  public synchronized boolean onListeningAcknowledged(WCN.Range past, WCN.Range future) {
    notify();
    return true;
  }

  public synchronized boolean onArtifactExists(Artifact artifact, Revision lastRevision) {
    myPlaceHolder[0] = artifact;
    notify();
    System.out.println(myName + " listener - exists (" + artifact + ")");
    return true;
  }

  public synchronized boolean onArtifactAppears(Artifact artifact, Revision lastRevision) {
    myPlaceHolder[1] = artifact;
    notify();
    System.out.println(myName + " listener - appears (" + artifact + ")");
    return true;
  }

  public synchronized boolean onArtifactDisappears(Artifact artifact, Revision lastSeenRevision,
    Revision unseenRevision)
  {
    myPlaceHolder[2] = artifact;
    notify();
    System.out.println(myName + " listener - disappears (" + artifact + ")");
    return true;
  }

  public synchronized boolean onArtifactChanges(Artifact artifact, Revision prevRevision, Revision newRevision) {
    myPlaceHolder[3] = artifact;
    notify();
    System.out.println(myName + " listener - changes (" + artifact + ")");
    return true;
  }

  public synchronized boolean onPastPassed(WCN.Range pastRange) {
    notify();
    return true;
  }

  public boolean onWCNPassed(WCN wcn) {
    return true;
  }

  public synchronized void assertValues(Artifact valueOnExists, Artifact valueOnAppears, Artifact valueOnDisappears,
    Artifact valueOnChanges)
  {
    boolean r = false;
    try {
      for (int i = 0; i < myAttempts; i++) {
        r = true;
        r = r && Util.equals(myPlaceHolder[0], valueOnExists);
        r = r && Util.equals(myPlaceHolder[1], valueOnAppears);
        r = r && Util.equals(myPlaceHolder[2], valueOnDisappears);
        r = r && Util.equals(myPlaceHolder[3], valueOnChanges);
        if (r)
          break;
        wait(myDelay);
      }
    } catch (InterruptedException e) {
      throw new Failure(e);
    }
    if (!r) {
      BaseTestCase.assertEquals(myName, valueOnExists, myPlaceHolder[0]);
      BaseTestCase.assertEquals(myName, valueOnAppears, myPlaceHolder[1]);
      BaseTestCase.assertEquals(myName, valueOnDisappears, myPlaceHolder[2]);
      BaseTestCase.assertEquals(myName, valueOnChanges, myPlaceHolder[3]);
    }
    clear();
  }

  synchronized void clear() {
    for (int i = 0; i < myPlaceHolder.length; i++)
      myPlaceHolder[i] = null;
  }
}
