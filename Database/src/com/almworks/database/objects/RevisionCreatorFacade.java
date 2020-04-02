package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.universe.Atom;
import com.almworks.api.universe.Expansion;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyed;
import com.almworks.util.events.EventSource;
import org.almworks.util.TypedKey;

public class RevisionCreatorFacade extends LongKeyed implements RevisionCreator {
  private RevisionCreatorImpl myBuildingRevision;
  private Revision myBuiltRevision;
  private final Basis myBasis;
  private boolean myWasEmpty = false;
  private final RevisionCreationContext myContext;
  private final RevisionSwitch myRevisionSwitch;
  private final RevisionWithInternals myRevisionDecorator;

  public RevisionCreatorFacade(Basis basis, Atom etherealAtom, Revision prevRevision, RevisionCreationContext context,
    EventSource<TransactionListener> eventSource)
  {
    super(etherealAtom.getAtomID());
    myBasis = basis;
    myContext = context;
    myBuildingRevision = new RevisionCreatorImpl(basis, etherealAtom, prevRevision);
    myRevisionSwitch = new RevisionSwitch(myBuildingRevision, context);
    myRevisionDecorator = new RevisionDecorator(basis, myRevisionSwitch);
    eventSource.addStraightListener(new TransactionListener.Adapter() {
      public void onBeforeUnderlyingCommit(Expansion underlying) {
        RevisionCreatorFacade.this.onBeforeUnderlyingCommit(underlying);
      }

      public void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
        RevisionCreatorFacade.this.onAfterUnderlyingCommit(underlying, success);
      }
    });
  }

  public Artifact getArtifact() {
    return myContext.getArtifact();
  }

  public long getPointerKey() {
    return getArtifact().getKey();
  }

  public synchronized void deleteObject() {
    buildDelegate().deleteObject();
  }

  public synchronized boolean isBuilt() {
    return myBuiltRevision != null;
  }

  public boolean isChanged(TypedKey<Attribute> systemAttribute) {
    return isChanged(myBasis.getSystemObject(systemAttribute));
  }

  public synchronized boolean isChanged(ArtifactPointer attribute) {
    return buildDelegate().isChanged(attribute);
  }

  public synchronized boolean isNew() {
    return buildDelegate().isNew();
  }

  public boolean setValue(TypedKey<Attribute> systemAttribute, Object value) {
    return setValue(myBasis.getSystemObject(systemAttribute), value);
  }

  public synchronized boolean setValue(ArtifactPointer attribute, Object value) {
    return buildDelegate().setValue(attribute, value);
  }

  public boolean unsetValue(TypedKey<Attribute> systemAttribute) {
    return unsetValue(myBasis.getSystemObject(systemAttribute));
  }

  public synchronized boolean unsetValue(ArtifactPointer attribute) {
    return buildDelegate().unsetValue(attribute);
  }

  public Revision asRevision() {
    return myRevisionDecorator;
  }

  public synchronized boolean isEmpty() {
    if (myBuiltRevision != null)
      return myWasEmpty;
    else
      return buildDelegate().isEmpty();
  }

  public synchronized void forceCreation() {
    buildDelegate().forceCreation();
  }

  public synchronized Value getChangingValue(ArtifactPointer attribute) {
    if (myBuiltRevision != null)
      return null;
    else
      return buildDelegate().getChangingValue(attribute);
  }

  public synchronized void onAfterUnderlyingCommit(Expansion underlying, boolean success) {
    RevisionCreatorImpl building = buildDelegate();
    myWasEmpty = building.isEmpty();
    building.onInternalAfterCommit(underlying);
    myBuildingRevision = null;
    myBuiltRevision =
      myWasEmpty ? building.getPrevRevision() : myBasis.getRevision(building.key(), myContext.getRevisionIterator());
    myRevisionSwitch.doSwitch(myBuiltRevision);
    assert myBuiltRevision != null : this + " is zombie";
  }

  public synchronized void onBeforeUnderlyingCommit(Expansion underlying) {
    buildDelegate().onInternalBeforeCommit(underlying);
  }

  private synchronized RevisionCreatorImpl buildDelegate() {
    if (myBuiltRevision != null)
      throw new IllegalStateException();
    return myBuildingRevision;
  }
}
