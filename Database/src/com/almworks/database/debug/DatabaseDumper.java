package com.almworks.database.debug;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.ArtifactType;
import com.almworks.util.DumperUtil;
import com.almworks.util.Pair;
import com.almworks.util.io.IOUtils;
import org.almworks.util.*;

import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DatabaseDumper {
  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private static final Comparator<Pair<String, String>> PAIR_COMPARATOR = new Comparator<Pair<String, String>>() {
    public int compare(Pair<String, String> pair1, Pair<String, String> pair2) {
      return String.CASE_INSENSITIVE_ORDER.compare(pair1.getFirst(), pair2.getFirst());
    }
  };

  public static void dump(final Workspace workspace, final String fileName) throws IOException, InterruptedException {
    final Exception[] ee = new Exception[1];
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          runDump(workspace, fileName);
        } catch (Exception e) {
          ee[0] = e;
        }
      }
    };
    if (EventQueue.isDispatchThread())
      new Thread(runnable).start();
    else
      runnable.run();
    Exception e = ee[0];
    if (e instanceof IOException)
      throw (IOException) e;
    else if (e instanceof InterruptedException)
      throw (InterruptedException) e;
    else if (e instanceof RuntimeException)
      throw (RuntimeException) e;
    else if (e != null)
      throw new Failure(e);
  }

  private static void runDump(final Workspace workspace, final String fileName) throws FileNotFoundException,
    InterruptedException {
    PrintStream out = null;
    try {
      FileOutputStream stream = new FileOutputStream(fileName);
      out = new PrintStream(stream);

      FilterManager filterManager = workspace.getFilterManager();
      Filter typesFilter = filterManager.attributeEquals(SystemObjects.ATTRIBUTE.TYPE, SystemObjects.TYPE.TYPE, true);
      Collection<Revision> types = workspace.getViews().getRootView().filter(typesFilter).getAllArtifacts();
      for (Iterator<Revision> iterator = types.iterator(); iterator.hasNext();) {
        dumpTyped(out, workspace, iterator.next());
      }
    } finally {
      if (out != null)
        IOUtils.closeStreamIgnoreExceptions(out);
    }
  }

  public static void dumpTyped(PrintStream out, Workspace workspace, ArtifactPointer type) throws InterruptedException {
    String typeName = Util.upper(type.getArtifact().getTyped(ArtifactType.class).getName());
    out.println();
    out.println();
    out.println("=====================================================================================");
    out.println("*** " + typeName + "");

    out.println("=====================================================================================");
    out.println();

    FilterManager filterManager = workspace.getFilterManager();
    Filter typedObjectsFilter = filterManager.attributeEquals(SystemObjects.ATTRIBUTE.TYPE, type, true);
    Collection<Revision> typedCollection = workspace.getViews().getRootView().filter(typedObjectsFilter).getAllArtifacts();
    for (Iterator<Revision> iterator = typedCollection.iterator(); iterator.hasNext();) {
      dumpArtifact(out, iterator.next());
    }
  }

  public static void dumpArtifact(PrintStream out, Revision revision) {
    Artifact artifact = revision.getArtifact();
    String header = "ARTIFACT A:" + artifact.getKey();
    if (artifact.getLastRevision().isDeleted())
      header = header + " DELETED";
    out.println(header);

    RCBArtifact rcb = artifact.getRCBExtension(false);
    if (rcb == null) {
      dumpChain(out, artifact, RevisionAccess.ACCESS_DEFAULT);
    } else {
      dumpRCBInfo(out, rcb);
      dumpChain(out, artifact, RevisionAccess.ACCESS_LOCAL);
      dumpChain(out, artifact, RevisionAccess.ACCESS_MAINCHAIN);
    }
  }

  private static void dumpRCBInfo(PrintStream out, RCBArtifact rcb) {
    out.println("hasConflict()==" + rcb.hasConflict());
    out.println("getLocalChanges().size()==" + rcb.getLocalChanges().size());
    out.println("isReincarnating()==" + rcb.isReincarnating());
  }

  private static void dumpChain(PrintStream out, Artifact artifact, RevisionAccess access) {
    out.println("CHAIN " + access);
    Revision revision = artifact.getLastRevision(access);
    if (revision.getPrevRevision() != null)
      dumpRevision(out, revision, true);
    while (revision != null) {
      Revision prevRevision = revision.getPrevRevision();
      dumpRevision(out, revision, prevRevision == null);
      revision = prevRevision;
    }
  }

  public static void dumpRevision(PrintStream out, Revision revision, boolean full) {
    String header = "  [" + revision.getWCN() + "] Revision A:" + revision.getKey();
    if (revision.isDeleted())
      header = header + " DELETED";
    if (!full)
      header = header + " (changes)";
    out.println(header);
    Map<ArtifactPointer, Value> filterMap;
    if (full) {
      filterMap = (Map) revision.getImage().getData();
    } else {
      filterMap = revision.getChanges();
    }
    List<Pair<String, String>> dataMap = Collections15.arrayList();
    for (Iterator<Map.Entry<ArtifactPointer, Value>> ii = filterMap.entrySet().iterator(); ii.hasNext();) {
      Map.Entry<ArtifactPointer, Value> entry = ii.next();
      Revision lastRevision = entry.getKey().getArtifact().getLastRevision();
      String name = lastRevision.getValue(SystemObjects.ATTRIBUTE.DISPLAYABLE_NAME);
      if (name == null)
        name = lastRevision.getValue(SystemObjects.ATTRIBUTE.NAME);
      dataMap.add(Pair.create(name, getValueString(entry.getValue())));
    }
    Collections.sort(dataMap, PAIR_COMPARATOR);
    DumperUtil.printMap(out, dataMap, 4);
    out.println();
  }

  public static String getValueString(Value value) {
    if (value == null)
      return "<UNSET>";

    Value[] arrayValues = value.getValue(Value[].class);
    if (arrayValues != null) {
      return getArrayValueString(value, arrayValues);
    }

    Artifact reference = value.getValue(Artifact.class);
    if (reference != null) {
      return getReferenceValueString(reference);
    }

    Integer integer = value.getValue(Integer.class);
    if (integer != null) {
      return integer.toString();
    }

    Date date = value.getValue(Date.class);
    if (date != null) {
      return DATE_FORMAT.format(date);
    }

    Boolean bool = value.getValue(Boolean.class);
    if (bool != null) {
      return Util.upper(bool.toString());
    }

    String string = value.getValue(String.class);
    if (string != null) {
      return "\"" + string + "\"";
    }

    byte[] bytes = value.getValue(byte[].class);
    if (bytes != null) {
      return DumperUtil.getBytesRepresentation(bytes);
    }

    return "unknown value " + value + ", type " + value.getType();
  }

  private static String getReferenceValueString(Artifact reference) {
    String ref = "R:" + reference.getKey();
    StringBuffer buf = new StringBuffer();
    Revision lastRevision = reference.getLastRevision();
    String name = lastRevision.getValue(SystemObjects.ATTRIBUTE.NAME);
    if (name == null)
      name = lastRevision.getValue(SystemObjects.ATTRIBUTE.ID);
    if (name == null)
      return ref;
    buf.append(name);
    buf.append(" (").append(ref);
/*
    ArtifactType type = lastRevision.getValue(SystemObjects.ATTRIBUTE.TYPE);
    String typeName = type == null ? null : type.getName();
    if (typeName != null)
      buf.appendText(", type ").appendText(typeName.toUpperCase());
*/
    buf.append(")");
    return buf.toString();
  }

  private static String getArrayValueString(Value value, Value[] arrayValues) {
    if (arrayValues.length == 0)
      return "{} [" + value.getType().toString() + "]";
    String[] stringValues = new String[arrayValues.length];
    for (int i = 0; i < arrayValues.length; i++)
      stringValues[i] = getValueString(arrayValues[i]);
//    Arrays.sort(stringValues, String.CASE_INSENSITIVE_ORDER);
    StringBuffer buf = new StringBuffer("{ ").append(stringValues[0]);
    for (int i = 1; i < arrayValues.length; i++) {
      buf.append("\n  ").append(stringValues[i]);
    }
    buf.append(" }");
    return buf.toString();
  }
}
