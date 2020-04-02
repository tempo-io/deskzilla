package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;

import java.util.logging.Level;

public class QueryPagingErrorLoggingRTests extends QueryPagingTests {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // We expect Log.error(), it's OK
    setTestFailLevel(Level.OFF);
  }

  public void testHitLoadLimitByOffset() {
    addFrame(0, 0, 3);
    for (int i = 0; i < QueryPaging.MAX_LOADS_PER_OFFSET + 1; ++i) {
      addFrameKeepProcs(2, 2, 5);
      addFrameKeepProcs(4, 4, 5);
      appendProcs(changeMtime(4));
    }
    mustThrow(ConnectorException.class, new RunnableE<ConnectorException>() {
      @Override
      public void run() throws ConnectorException {
        loadBugs();
      }
    });
  }
  
}
