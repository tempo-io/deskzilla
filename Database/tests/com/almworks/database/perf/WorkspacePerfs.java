package com.almworks.database.perf;

import com.almworks.api.database.RevisionCreator;
import com.almworks.api.database.SystemObjects;
import com.almworks.api.database.util.*;
import com.almworks.database.*;
import com.almworks.universe.MemUniverse;
import org.almworks.util.Collections15;

import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WorkspacePerfs extends WorkspaceFixture {
  private MySingletons mySingletons;

  public WorkspacePerfs() {
    super(300000);
  }

  public void testNothing() {
    // stub for intellij test runner
  }

  public void perfWorkspaceStart() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      myUniverse = new MemUniverse();
      myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
      myBasis.start();
      myWorkspace = new WorkspaceImpl(myBasis);
      myWorkspace.start();
      createSingletons();
      if (i % 10 == 0) {
        long stop = System.currentTimeMillis();
        System.out.println(i + " " + (stop - start) + "ms");
        start = stop;
      }
    }
  }

  private void createSingletons() {
    mySingletons = new MySingletons();
    mySingletons.initialize(myWorkspace);
  }


  private static class MySingletons extends SingletonCollection implements SystemObjects {
    private final List<Singleton> myList = Collections15.arrayList();

    public MySingletons() {
      for (int i = 0; i < 100; i++) {
        final int index = i;
        myList.add(singleton("test:singleton:id:" + index, new Initializer() {
          public void initialize(RevisionCreator creator) {
            creator.setValue(ATTRIBUTE.TYPE, TYPE.EXTERNAL_ARTIFACT);
            creator.setValue(ATTRIBUTE.IS_PROTOTYPE, Boolean.FALSE);
            creator.setValue(ATTRIBUTE.NAME, "name " + index);
          }
        }));
      }
    }
  }
}
