package com.almworks.database.migration.upgrade;

import com.almworks.api.database.*;
import com.almworks.api.database.util.SingletonNotFoundException;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.database.filter.FilterManagerImpl;
import com.almworks.database.migration.MigrationController;
import com.almworks.database.migration.MigrationFailure;
import com.almworks.database.typed.*;
import com.almworks.universe.FileUniverse;
import com.almworks.universe.data.*;
import com.almworks.util.exec.LongEventQueue;

import java.util.Iterator;
import java.util.Map;

public class ValueMarshallingCompactizationMigration extends UpgradeMigration implements ExpansionInfoSink {
  private final WorkArea myWorkArea;
  private MigrationController myController;
  private ExpansionInfoBuilder myBuilder = new ExpansionInfoBuilder();
  private FileUniverse myUniverse;
  private Basis myBasis1;
  private Basis myBasis2;
  private ValueFactoryImpl2 myNewFactory;

  public ValueMarshallingCompactizationMigration(WorkArea workArea, MigrationController controller) {
    myWorkArea = workArea;
    myController = controller;
  }

  public void migrate() throws MigrationFailure {
    LongEventQueue.installToContext();
    myController.startMigration(myWorkArea);
    myUniverse = new FileUniverse(myWorkArea);
    myUniverse.setReadOnly(true);
    myUniverse.start();
    myBasis1 = new Basis(myUniverse, ConsistencyWrapper.FAKE);
    myBasis1.start();
    myBasis2 = new Basis(myUniverse, ConsistencyWrapper.FAKE, new String[] {Basis.INITPARAM_VALUETYPES_2});
    myBasis2.start();
    WorkspaceMaintenance.repair(myUniverse);
    FilterManager filman = new FilterManagerImpl(myBasis1, myBasis1);
    SystemViewsImpl views = new SystemViewsImpl(myBasis1, filman);
    try {
      myBasis1.ourSingletons.initializeReadOnly(views.getRootView(), filman);
    } catch (SingletonNotFoundException e) {
      throw new MigrationFailure("database is not in valid state", e);
    }
    myBasis1.VALUETYPE.installDefaultValueTypes(myBasis1.ourValueFactory);
    myBasis1.installTypedObjectFactory(new AttributeImpl.Factory(myBasis1));
    myBasis1.installTypedObjectFactory(new ValueTypeDescriptorImpl.Factory(myBasis1));
    myBasis1.installTypedObjectFactory(new ArtifactTypeImpl.Factory(myBasis1));

    myNewFactory = new ValueFactoryImpl2(ConsistencyWrapper.FAKE, myBasis1);
    myBasis1.VALUETYPE.installDefaultValueTypes2(myNewFactory);
    myUniverse.stop();

    myController.makePass(this);
    myController.endMigration();
  }

  public boolean visitExpansionInfo(ExpansionInfo info) {
    myBuilder.clear();
    myBuilder.setUCN(info.UCN);
    for (int i = 0; i < info.atoms.length; i++) {
      Atom atom = info.atoms[i];
      Map<Long, Particle> junctions = atom.copyJunctions();
      boolean changed = false;
      for (Iterator<Long> ii = junctions.keySet().iterator(); ii.hasNext();) {
        Long key = ii.next();
        if (key.longValue() > 0) {
          Particle p = junctions.get(key);
          if (p instanceof Particle.PBytes) {
            Particle newP = convert(p);
            if (newP != null) {
              junctions.put(key, newP);
              changed = true;
            }
          }
        }
      }
      Atom newAtom = new Atom(atom.getAtomID(), junctions.size());
      for (Iterator<Map.Entry<Long, Particle>> ii = junctions.entrySet().iterator(); ii.hasNext();) {
        Map.Entry<Long, Particle> entry = ii.next();
        newAtom.buildJunction(entry.getKey().longValue(), entry.getValue());
      }
      myBuilder.addAtom(newAtom);
    }
    myController.saveExpansion(myBuilder.build());
    return true;
  }

  private Particle convert(Particle particle) {
    try {
      Value value = myBasis1.ourValueFactory.unmarshall(particle);
      value = recreateValue(value);
      Object object = myNewFactory.marshall(value);
      return Particle.create(object);
    } catch (DatabaseInconsistentException e) {
      // todo log
      return null;
    }
  }

  private Value recreateValue(Value value) throws DatabaseInconsistentException {
    ValueType type = value.getType();
    ValueType newType = myNewFactory.getType(myBasis1.ourValueFactory.getTypeArtifact(type));
    if (newType.equals(type))
      return value;
    value = newType.create(value);
    return value;
  }
}
