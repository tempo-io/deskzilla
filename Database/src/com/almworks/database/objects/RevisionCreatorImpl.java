package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.*;
import com.almworks.database.*;
import com.almworks.database.value.ValueInternals;
import com.almworks.util.cache2.LongKeyed;
import com.almworks.util.collections.MapIterator;
import gnu.trove.TLongObjectHashMap;
import gnu.trove.TLongObjectIterator;
import org.almworks.util.Collections15;
import org.almworks.util.Util;

import java.util.HashMap;
import java.util.Map;


class RevisionCreatorImpl extends LongKeyed implements InternalTransactionListener, RevisionInternals {
  private final Atom myAtom;
  private final Basis myBasis;
  private final TLongObjectHashMap/*<Value>*/ myValues = new TLongObjectHashMap();
  private final Revision myPrevRevision;

  private boolean myCreationForced = false;

  public RevisionCreatorImpl(Basis basis, Atom etherealAtom, Revision prevRevision) {
    super(etherealAtom.getAtomID());
    assert basis != null;
    myBasis = basis;
    myAtom = etherealAtom;
    myPrevRevision = prevRevision;
  }

  public synchronized void onInternalAfterCommit(Expansion underlying) {
    myValues.clear();
  }

  public synchronized void onInternalBeforeCommit(Expansion underlying) {
    if (isEmpty()) {
      underlying.discardAtom(myAtom);
      return;
    }

    for (TLongObjectIterator ii = myValues.iterator(); ii.hasNext();) {
      ii.advance();
      long key = ii.key();
      Value value = (Value) ii.value();
      respectReferences(underlying, key, value);
      myAtom.buildJunction(key, Particle.create(myBasis.ourValueFactory.marshall(value)));
    }
    if (myPrevRevision != null)
      underlying.addVerifier(new VerifyPhysicalChainLastAtom(myBasis, myAtom));
  }

  private void respectReferences(Expansion underlying, long attributeKey, Value value) {
    underlying.atomReferred(attributeKey);
    if (value instanceof ValueInternals) {
      long[] referredKeys = ((ValueInternals) value).getReferredKeys();
      if (referredKeys != null) {
        for (long atomKey : referredKeys) {
          underlying.atomReferred(atomKey);
        }
      }
    }
  }

  public Atom getAtom() {
    return myAtom;
  }

  public RevisionInternals getPrevRevisionInternals() {
    return DBUtil.getInternals(getPrevRevision());
  }

  public void invalidateValuesCache(RevisionAccess access) {
  }

  public void deleteObject() {
    setValue(myBasis.ATTRIBUTE.deleted, Boolean.TRUE);
  }

  public synchronized void forceCreation() {
    myCreationForced = true;
  }

  public synchronized Value get(ArtifactPointer pointer) {
    long key = key(pointer);
    Value value = (Value) myValues.get(key);
    if (value == null && myPrevRevision != null)
      value = myPrevRevision.getValues().get(pointer);
    if (value == ValueFactoryImpl.UNSET)
      value = null;
    return value;
  }

  public Revision getPrevRevision() {
    return myPrevRevision;
  }

  public WCN getWCN() {
    return WCN.EARLIEST;
  }

  public synchronized boolean isChanged(ArtifactPointer attribute) {
    return myValues.containsKey(key(attribute));
  }

  public synchronized boolean isEmpty() {
    return !myCreationForced && myPrevRevision != null && myValues.size() == 0;
  }

  public boolean isNew() {
    return myPrevRevision == null;
  }

  public MapIterator<ArtifactPointer, Value> iterator() {
    return new VersionedObjectIterator(myBasis, this);
  }

  /**
   * @return true if changed
   */
  public synchronized boolean setValue(ArtifactPointer attribute, Object valueData) {
    boolean unset = valueData == ValueFactoryImpl.UNSET;
    Value value = unset ? null : myBasis.ourValueFactory.create(valueData);
    Value oldValue = get(attribute);
    if (Util.equals(value, oldValue))
      return false;
    if (myPrevRevision != null) {
      // unset value if reverting to old version's value
      oldValue = myPrevRevision.getValue(attribute);
      if (Util.equals(value, oldValue)) {
        myValues.remove(key(attribute));
        return true;
      }
    }
    if (myPrevRevision == null && unset) {
      myValues.remove(key(attribute));
      return true;
    }
    myValues.put(key(attribute), unset ? ValueFactoryImpl.UNSET : value);
    return true;
  }

  public boolean unsetValue(ArtifactPointer attribute) {
    return setValue(attribute, ValueFactoryImpl.UNSET);
  }

  private long key(ArtifactPointer attribute) {
    return attribute.getPointerKey();
  }

  public Map<ArtifactPointer, Value> getChanges() {
    HashMap<ArtifactPointer, Value> result = Collections15.hashMap();
    for (TLongObjectIterator ii = myValues.iterator(); ii.hasNext();) {
      ii.advance();
      result.put(myBasis.getArtifact(ii.key()), (Value) ii.value());
    }
    return result;
  }

  public Value getChangingValue(ArtifactPointer attribute) {
    synchronized (this) {
      Value value = (Value) myValues.get(key(attribute));
      return value == ValueFactoryImpl.UNSET ? null : value;
    }
  }
}
