package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.Particle;
import com.almworks.database.*;
import com.almworks.util.collections.MapIterator;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
class VersionedObjectIterator implements MapIterator<ArtifactPointer, Value> {
  private RevisionInternals myCurrentProxy;
  private Iterator<Map.Entry<Long, Particle>> myCurrentIterator = null;
  private final Set<Artifact> myIteratedKeys = Collections15.hashSet();
  private Artifact myLastKey;
  private Value myLastValue;
  private boolean myIterated = false;
  private boolean myFinished = false;
  private final Basis myBasis;

  public VersionedObjectIterator(Basis basis, RevisionInternals proxy) {
    myBasis = basis;
    myCurrentProxy = proxy;
  }

  public synchronized boolean next() {
    if (myFinished)
      return false;
    myIterated = true;
    while (myCurrentProxy != null) {
      if (myCurrentIterator == null)
        myCurrentIterator = myCurrentProxy.getAtom().copyJunctions().entrySet().iterator();
      if (!myCurrentIterator.hasNext()) {
        myCurrentProxy = myCurrentProxy.getPrevRevisionInternals();
        myCurrentIterator = null;
        continue;
      }
      final Map.Entry<Long, Particle> entry = myCurrentIterator.next();
      long key = entry.getKey().longValue();
      if (key < 0)
        continue;
      myLastKey = myBasis.getArtifact(key); // :todo: check atom exists and is Artifact-marked
      if (myIteratedKeys.contains(myLastKey))
        continue; // already had it
      myIteratedKeys.add(myLastKey);

      while (true) {
        try {
          Value value = myBasis.ourValueFactory.unmarshall(entry.getValue());
          if (value == null)
            throw new DatabaseInconsistentException("unknown value in " + entry.getValue());
          myLastValue = value;
          break;
        } catch (DatabaseInconsistentException e) {
          myBasis.ourConsistencyWrapper.handle(e, -1);
        }
      }

      if (myLastValue == null || myLastValue == ValueFactoryImpl.UNSET)
        continue;

      // myLastKey and myLastValue are set
      return true;
    }
    myFinished = true;
    return false;
  }

  public synchronized ArtifactPointer lastKey() throws NoSuchElementException {
    if (!myIterated || myFinished)
      throw new NoSuchElementException();
    return myLastKey;
  }

  public synchronized Value lastValue() throws NoSuchElementException {
    if (!myIterated || myFinished)
      throw new NoSuchElementException();
    return myLastValue;
  }
}
