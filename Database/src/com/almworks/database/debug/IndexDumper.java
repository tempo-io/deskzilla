package com.almworks.database.debug;

import com.almworks.database.bitmap.AbstractBitmapIndex;
import org.almworks.util.RuntimeInterruptedException;
import util.external.BitSet2;

import java.io.*;

public class IndexDumper {
  public static void dump(AbstractBitmapIndex index, File file) throws IOException {
    FileOutputStream stream = new FileOutputStream(file);
    PrintStream out = new PrintStream(stream);

    out.println("INDEX " + index.getKey().getIndexKey());
    out.println();
    out.println("Strategy " + index.getStrategy());
    out.println("Filter " + index.getSystemFilter());
    out.println("Dirty " + index.isDirty());
    out.println();
    out.println();
    try {
      BitSet2 bits = index.getAtomBitsForReading();
      dumpBits(bits, out);

    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
    out.close();
    stream.close();
  }

  private static void dumpBits(BitSet2 bits, PrintStream out) {
    out.println("BITS [size=" + bits.size() + ", cardinality=" + bits.cardinality() + "]");
    for (int i = bits.prevSetBit(bits.size()); i >= 0; i = bits.prevSetBit(i - 1))
      out.println(i);
  }

  public static void dumpBits(BitSet2 bits, String fileName) throws IOException {
    FileOutputStream stream = new FileOutputStream(fileName);
    PrintStream out = new PrintStream(stream);
    dumpBits(bits, out);
    out.close();
    stream.close();
  }
}
