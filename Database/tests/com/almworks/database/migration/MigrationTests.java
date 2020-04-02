package com.almworks.database.migration;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.typed.ValueTypeDescriptor;
import com.almworks.api.install.Setup;
import com.almworks.api.install.TrackerProperties;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.misc.TestWorkArea;
import com.almworks.universe.FileUniverse;
import com.almworks.universe.data.ExpansionInfo;
import com.almworks.universe.data.ExpansionInfoSink;
import com.almworks.util.collections.Convertor;
import com.almworks.util.exec.LongEventQueue;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Log;
import org.almworks.util.Util;
import util.external.CompactChar;

import java.io.*;
import java.util.*;

public class MigrationTests extends BaseTestCase {
  private MigrationControllerImpl myMigrator;
  private Object mySavedCompatibility;
  private TestWorkArea myTestWorkArea;
  private FileUniverse myUniverse;
  private Basis myBasis;
  private WorkspaceImpl myWorkspace;
  private Attribute myAttributeOne;
  private Attribute myAttributeTwo;

  protected void setUp() throws Exception {
    super.setUp();
    WorkspaceStatic.cleanup();
    Setup.cleanupForTestCase();
    mySavedCompatibility = System.getProperties().put(TrackerProperties.COMPATIBILITY_LEVEL, "1000000");
    myTestWorkArea = new TestWorkArea();
    myUniverse = new FileUniverse(myTestWorkArea);
    LongEventQueue.installToContext();
    myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis.start();
    myWorkspace = new WorkspaceImpl(myBasis);
    myUniverse.start();
    myWorkspace.repair();
    myWorkspace.open();
    myAttributeOne = createAttribute("one");
    myAttributeTwo = createAttribute("two");
    myMigrator = new MigrationControllerImpl();
  }

  protected void tearDown() throws Exception {
    myWorkspace.stop();
    myUniverse.stop();
    myMigrator = null;
    myWorkspace = null;
    myUniverse = null;
    LongEventQueue.removeFromContext();
    if (mySavedCompatibility == null)
      System.getProperties().remove(TrackerProperties.COMPATIBILITY_LEVEL);
    else
      System.getProperties().put(TrackerProperties.COMPATIBILITY_LEVEL, mySavedCompatibility);
    myTestWorkArea.cleanUp();
    myBasis = null;
    Setup.cleanupForTestCase();
    super.tearDown();
  }

  public void testSimpleMigration() throws MigrationFailure, InterruptedException {
    ValueTypeDescriptor object = myWorkspace.getSystemObject(SystemObjects.VALUETYPE.PLAINTEXT);
    final long stringTypeID = object.getArtifact().getKey();

    myWorkspace.stop();
    long oldUcn = myUniverse.getUCN();
    myUniverse.stop();

    myMigrator.startMigration(myTestWorkArea);
    myMigrator.makePass(new ExpansionInfoSink() {
      public boolean visitExpansionInfo(ExpansionInfo info) {
        Atom[] newAtoms = new Atom[info.atoms.length];
        for (int i = 0; i < info.atoms.length; i++) {
          Atom atom = info.atoms[i];
          Map<Long, Particle> junctions = atom.copyJunctions();
          Atom newAtom = new Atom(atom.getAtomID(), junctions.size());
          for (Iterator<Map.Entry<Long, Particle>> ii = junctions.entrySet().iterator(); ii.hasNext();) {
            Map.Entry<Long, Particle> entry = ii.next();
            long key = entry.getKey().longValue();
            Particle p = entry.getValue();
            if (p instanceof Particle.PBytes) {
              p = makeUppercase(p, stringTypeID);
            }
            newAtom.buildJunction(key, p);
          }
          newAtom.buildFinished(info.UCN);
          newAtoms[i] = newAtom;
        }
        ExpansionInfo newInfo = new ExpansionInfo(info.UCN, newAtoms);
        myMigrator.saveExpansion(newInfo);
        return true;
      }
    });
    myMigrator.endMigration();

    myUniverse = new FileUniverse(myTestWorkArea);
    myUniverse.start();
    myBasis = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis.start();
    myWorkspace = new WorkspaceImpl(myBasis);
    assertEquals(oldUcn, myUniverse.getUCN());
    myWorkspace.repair();
    myWorkspace.open();
    ArtifactView view = myWorkspace.getViews().getUserView();
    Collection<Revision> userObjects = view.getAllArtifacts();
    Set<String> names = new Convertor<Revision, String>() {
      public String convert(Revision revision) {
        return revision.getValue(SystemObjects.ATTRIBUTE.NAME);
      }
    }.collectSet(userObjects);
    new CollectionsCompare().unordered(names, new String[] {"ONE", "TWO"});
  }

  private Particle makeUppercase(Particle p, long typeID) {
    ByteArrayInputStream bais = new ByteArrayInputStream(p.raw());
    ByteArrayOutputStream baos = new ByteArrayOutputStream(bais.available());
    DataInputStream in = new DataInputStream(bais);
    try {
      long id = in.readLong();
      // long id = CompactInt.readLong(in);
      if (id != typeID)
        return p;
      String s = CompactChar.readString(in);
      s = Util.upper(s);
      DataOutputStream out = new DataOutputStream(baos);
      out.writeLong(id);
      CompactChar.writeString(out, s);
      out.close();
      Particle newP = new Particle.PBytes(baos.toByteArray());
      return newP;
    } catch (IOException e) {
      Log.debug(e);
      return p;
    }
  }


  private Attribute createAttribute(String attributeName) {
    Transaction transaction = myWorkspace.beginTransaction();
    RevisionCreator creator = transaction.createArtifact(Attribute.class);
    Attribute attribute = creator.getArtifact().getTyped(Attribute.class);
    creator.setValue(attribute.attributeName(), attributeName);
    creator.setValue(SystemObjects.ATTRIBUTE.VALUETYPE, SystemObjects.VALUETYPE.PLAINTEXT);
    transaction.commitUnsafe();
    return attribute;
  }
}
