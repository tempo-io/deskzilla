package com.almworks.api.database.util;

import com.almworks.api.database.CollisionException;

public interface DatabaseRunnable {
  public void run() throws CollisionException;
}
