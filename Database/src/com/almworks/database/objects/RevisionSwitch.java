package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.util.collections.MapIterator;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
class RevisionSwitch implements RevisionRefined {
  private RevisionCreatorImpl myBuildingRevision;
  private Revision myBuiltRevision = null;
  private final RevisionCreationContext myContext;

  RevisionSwitch(RevisionCreatorImpl buildingRevision, RevisionCreationContext context) {
    assert buildingRevision != null;
    assert context != null;
    myBuildingRevision = buildingRevision;
    myContext = context;
  }

  public synchronized Value getValue(ArtifactPointer pointer) {
    return myBuiltRevision != null ? myBuiltRevision.getValue(pointer) : myBuildingRevision.get(pointer);
  }

  public synchronized Atom getAtom() {
    if (myBuiltRevision != null)
      return DBUtil.getInternals(myBuiltRevision).getAtom();
//      throw new UnsupportedOperationException("cannot access atom for created object");
    else
      return myBuildingRevision.getAtom();
  }

  public RevisionChain getChain() {
    return myContext.getRevisionChain();
  }

  public synchronized WCN getWCN() {
    return myBuiltRevision != null ? myBuiltRevision.getWCN() : myBuildingRevision.getWCN();
  }

  public synchronized long getKey() {
    return myBuiltRevision != null ? myBuiltRevision.getKey() : myBuildingRevision.key();
  }

  public synchronized Revision getPrevRevision() {
    return myBuiltRevision != null ? myBuiltRevision.getPrevRevision() : myBuildingRevision.getPrevRevision();
  }

  public synchronized MapIterator<ArtifactPointer, Value> iterator() {
    return myBuiltRevision != null ? myBuiltRevision.getValues().iterator() : myBuildingRevision.iterator();
  }

  public Map<ArtifactPointer, Value> getChanges() {
    return myBuiltRevision != null ? myBuiltRevision.getChanges() : myBuildingRevision.getChanges();
  }

  public RevisionIterator getRevisionIterator() {
    return myBuiltRevision != null ? ((RevisionDecorator)myBuiltRevision).getRevisionIterator() : myContext.getRevisionIterator();
  }

  public RevisionInternals getPrevRevisionInternals() {
    return DBUtil.getInternals(getPrevRevision());
  }

  public synchronized void invalidateValuesCache(RevisionAccess access) {
    if (myBuiltRevision != null)
      DBUtil.getInternals(myBuiltRevision).invalidateValuesCache(access);
    else
      myBuildingRevision.invalidateValuesCache(access);
  }

  public synchronized void forceCreation() {
    RevisionCreatorImpl creator = myBuildingRevision;
    if (creator != null) {
      creator.forceCreation();
    }
  }

  synchronized void doSwitch(Revision builtRevision) {
    myBuiltRevision = builtRevision;
    myBuildingRevision = null;
  }
}
