package com.almworks.universe.optimize;

import com.almworks.api.universe.Particle;
import com.almworks.util.io.IOUtils;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;

import java.io.*;
import java.util.List;

public class UniverseMemoryOptimizerTests extends BaseTestCase {
  private UniverseMemoryOptimizer myOptimizer;

  protected void setUp() throws Exception {
    super.setUp();
    myOptimizer = new UniverseMemoryOptimizer();
    UniverseMemoryOptimizer.staticCleanup();
  }

  protected void tearDown() throws Exception {
    UniverseMemoryOptimizer.staticCleanup();
    myOptimizer = null;
    super.tearDown();
  }

  public void test() {
    byte[] array = new byte[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1};
    checkArray(array);
  }

  public void testHosted() {
    checkArray(createTestData(1000, 3));
    checkArray(createTestData(999, 4));
    checkArray(createTestData(1001, 5));
    assertEquals(3000, UniverseMemoryOptimizer.size());
  }

  public void testLargeHostBytes() throws IOException {
    doTest(99999);
  }

  public void testBoundary() throws IOException {
    checkArray(createTestData(1, 9));
    doTest((1 << 14) - 1);
  }

  public void testBoundary2() throws IOException {
    doTest(1 << 15);
  }

  private void doTest(int count) throws IOException {
    byte[] data = createTestData(count, 1);
    checkArray(data);
    Particle p1 = myOptimizer.convert(Particle.createBytes(data));
    Particle p2 = myOptimizer.convert(Particle.createBytes(data));
    assertTrue(p1.equals(p2));
    assertTrue(p2.equals(p1));
    Particle unoptimized = Particle.createBytes(data);
    assertTrue(p1.equals(unoptimized));
    assertTrue(unoptimized.equals(p1));
    byte[] xdata = createTestData(count, 2);
    Particle p3 = Particle.createBytes(xdata);
    assertFalse(p1.equals(p3));
    assertFalse(p3.equals(p1));

    InputStream in = p1.getStream();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    long sz = IOUtils.transfer(in, out);
    assertEquals(count, sz);
    checkSame(data, out.toByteArray());

    Particle p4 = myOptimizer.convert(p3);
    assertFalse(p4.equals(p1));
    assertFalse(p1.equals(p4));
  }

  private void checkArray(byte[] array) {
    Particle original = Particle.createBytes(array);
    Particle optimized = myOptimizer.convert(original);
    byte[] optimizedBytes = optimized.raw();
    checkSame(array, optimizedBytes);
    assertEquals(original, optimized);
    assertEquals(optimized, original);
    assertEquals(optimized, optimized);
  }

  private void checkSame(byte[] sample, byte[] tested) {
    new CollectionsCompare().order(byteList(sample), byteList(tested));
  }

  private List<Byte> byteList(byte[] tested) {
    List<Byte> list = Collections15.arrayList();
    for (byte b : tested) {
      list.add(b);
    }
    return list;
  }
}
