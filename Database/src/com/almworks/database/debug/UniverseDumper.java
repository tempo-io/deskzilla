package com.almworks.database.debug;

import com.almworks.api.universe.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.*;
import org.almworks.util.Collections15;

import java.io.*;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class UniverseDumper {
  public static void dump(Universe universe, String fileName) throws IOException, InterruptedException {
    FileOutputStream stream = new FileOutputStream(fileName);
    PrintStream out = new PrintStream(stream);

    Iterator<Atom> ii = universe.getGlobalIndex().all();
    while (ii.hasNext()) {
      Atom atom = ii.next();
      dumpAtom(out, atom);
    }

    out.close();
    stream.close();
  }

  private static void dumpAtom(PrintStream out, Atom atom) {
    out.println("[" + atom.getUCN() + "] ATOM " + atom.getAtomID());
    List<Pair<String, String>> junctions = Collections15.arrayList();
    SortedMap<Long, Particle> map = Collections15.treeMap();
    map.putAll(atom.copyJunctions());
    Iterator<Map.Entry<Long, Particle>> ii = map.entrySet().iterator();
    while (ii.hasNext()) {
      Map.Entry<Long, Particle> entry = ii.next();
      String key = getLongRepresentation(entry.getKey().longValue());
      String value = getValueString(entry.getValue());
      junctions.add(Pair.create(key, value));
    }
    DumperUtil.printMap(out, junctions, 2);
    out.println();
  }

  private static String getValueString(Particle value) {
    if (value instanceof Particle.PLong) {
      return getLongRepresentation(((Particle.PLong) value).getValue());
    } else if (value instanceof Particle.PIsoString) {
      return "\"" + ((Particle.PIsoString) value).getValue() + "\"";
    } else if (value instanceof Particle.PBytes) {
      return DumperUtil.getBytesRepresentation(value.raw());
    } else if (value == null) {
      return "!!null!!";
    } else {
      return DumperUtil.getBytesRepresentation(value.raw()) + "    " + value.toString();
    }
  }

  private static String getLongRepresentation(long value) {
    NamedLong namedLong = Schema.ALL.get(new Long(value));
    if (namedLong == null)
      return Long.toString(value);
    else
      return namedLong.toString();
  }
}
