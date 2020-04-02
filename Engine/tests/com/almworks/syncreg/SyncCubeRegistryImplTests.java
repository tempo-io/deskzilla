package com.almworks.syncreg;

import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.io.*;
import java.util.SortedSet;

public class SyncCubeRegistryImplTests extends BaseTestCase {
  private SyncCubeRegistryImpl myRegistry;

  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = new SyncCubeRegistryImpl();
  }

  protected void tearDown() throws Exception {
    myRegistry = null;
    super.tearDown();
  }

  public void testFullSync() {
    assertFalse(myRegistry.isSynced(cube("")));
    myRegistry.setSynced(new ItemHypercubeImpl());
    assertTrue(myRegistry.isSynced(cube("")));
    myRegistry.setUnsynced(cube("1 +1"));
    assertFalse(myRegistry.isSynced(cube("")));
  }

  public void testSimple1() {
    myRegistry.setSynced(cube("1 +1"));
    assertTrue(myRegistry.isSynced(cube("1 +1")));
    assertTrue(myRegistry.isSynced(cube("1 +1 -2")));
    assertTrue(myRegistry.isSynced(cube("1 +1; 2 +1,2,3")));
    assertTrue(myRegistry.isSynced(cube("1 +1; 2 +1,2,3; 3 -5,6")));

    assertFalse(myRegistry.isSynced(cube("")));
    assertFalse(myRegistry.isSynced(cube("1 +1,2")));
    assertFalse(myRegistry.isSynced(cube("1 +1,2; 2 +1")));
    assertFalse(myRegistry.isSynced(cube("1 -1")));
  }

  public void testSimple2() {
    myRegistry.setSynced(cube("1 -1"));
    assertTrue(myRegistry.isSynced(cube("1 +2")));
    assertTrue(myRegistry.isSynced(cube("1 +2 -3")));
    assertTrue(myRegistry.isSynced(cube("1 -1,2")));
    assertTrue(myRegistry.isSynced(cube("1 +3; 2 +1,2,3")));
    assertTrue(myRegistry.isSynced(cube("1 +4; 2 +1,2,3; 3 -5,6")));

    assertFalse(myRegistry.isSynced(cube("")));
    assertFalse(myRegistry.isSynced(cube("1 +1")));
    assertFalse(myRegistry.isSynced(cube("1 +1,2")));
    assertFalse(myRegistry.isSynced(cube("1 -2")));
    assertFalse(myRegistry.isSynced(cube("1 +1,2; 2 +1")));
  }

  public void testUnion() {
    myRegistry.setSynced(cube("1 +1"));
    myRegistry.setSynced(cube("1 +2"));
    // todo make this test pass
    //assertTrue(myRegistry.isSynced(cube("1 +1,2")));
  }

  public void testPersistance() throws IOException {
    checkPersistence("");
    checkPersistence("1 +1");
    checkPersistence("1 +1; 2 +2; 3 +3; 4 +4; 5 +5; 6 +6; 7 +7; 8 +8");
    checkPersistence("1 -1; 2 -2; 3 -3; 4 -4; 5 -5; 6 -6; 7 -7; 8 -8");
    checkPersistence("1 +1", "1 +2", "1 +3", "1 +4", "2 -1", "2 -2", "2 -3", "2 -4");
    checkPersistence("1 +100000000001,100000000002,100000000003 -1,2,3");
    checkPersistence("1 +100000000001,100000000002,100000000003 -1,2,3", "2 +2; 3 -3");
    checkPersistence("1 +1", "2 +2; 3 +3", "2 +2,3; 3 +3,4", "5 +6,7,8; 6 -100,101,102; 7 -1");
  }

  private void checkPersistence(String ... cubes) throws IOException {
    SyncCubeRegistryImpl r1 = new SyncCubeRegistryImpl();
    for (String cube : cubes) {
      r1.setSynced(cube(cube));
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    r1.save(new DataOutputStream(out));

    SyncCubeRegistryImpl r2 = new SyncCubeRegistryImpl();
    ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
    r2.load(new DataInputStream(in));

    assertTrue(r1.equalRegistry(r2));
  }


  private ItemHypercubeImpl cube(String formula) {
    NumberedCube cube = EquidimensionalSetTests.cube(formula);
    ItemHypercubeImpl hypercube = new ItemHypercubeImpl();
    String[] attributes = cube.getAttributes();
    for (int i = 0; i < attributes.length; i++) {
      String attribute = attributes[i];
      long[] included = cube.getIncludedValues(i);
      long[] excluded = cube.getExcludedValues(i);
      if (included != null)
        hypercube.addAxisIncluded(DBAttribute.Int(attribute, attribute), createValueSet(included));
      if (excluded != null)
        hypercube.addAxisExcluded(DBAttribute.Int(attribute, attribute), createValueSet(excluded));
    }
    return hypercube;
  }

  private SortedSet<Long> createValueSet(long[] included) {
    SortedSet<Long> result = Collections15.treeSet();
    for (long p : included) {
      result.add(p);
    }
    return result;
  }
}
