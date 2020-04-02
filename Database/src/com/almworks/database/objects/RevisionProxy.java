package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyedProxy;
import com.almworks.util.cache2.NoCache;
import com.almworks.util.collections.MapIterator;
import com.almworks.util.threads.Threads;

import java.util.Collections;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RevisionProxy extends LongKeyedProxy<RevisionImpl> implements RevisionRefined, RevisionInternals {
  private final Basis myBasis;
  private final RevisionIterator myRevisionIterator;

  public RevisionProxy(long key, Basis basis, RevisionIterator revisionIterator) {
    super(key);
    assert revisionIterator != null;
    myBasis = basis;
    myRevisionIterator = revisionIterator;
  }

  public NoCache<RevisionImpl> getCache() {
    return myBasis.getRevisionCache();
  }

  public RevisionInternals getPrevRevisionInternals() {
    return DBUtil.getInternals(getPrevRevision());
  }

  public Value getValue(ArtifactPointer attribute) {
    Threads.assertLongOperationsAllowed();
    return delegate().get(attribute, myRevisionIterator);
  }

  public Atom getAtom() {
    return delegate().getAtom();
  }

  public void invalidateValuesCache(RevisionAccess access) {
    RevisionImpl d = delegate();
    if (d != null)
      d.invalidateValuesCache(access);
  }

  public RevisionChain getChain() {
    Threads.assertLongOperationsAllowed();
    return myRevisionIterator.getChain(myBasis, myKey);
  }

  public WCN getWCN() {
    return delegate().getWCN();
  }

  public long getKey() {
    return key();
  }

  public RevisionWithInternals getPrevRevision() {
    Threads.assertLongOperationsAllowed();
    return myRevisionIterator.getPreviousRevision(myBasis, getAtom());
  }

  public MapIterator<ArtifactPointer, Value> iterator() {
    Threads.assertLongOperationsAllowed();
    return new VersionedObjectIterator(myBasis, this);
  }

  public Map<ArtifactPointer, Value> getChanges() {
    RevisionImpl d = delegate();
    return d == null ? Collections.<ArtifactPointer, Value>emptyMap() : d.getChanges();
  }

  public RevisionIterator getRevisionIterator() {
    return myRevisionIterator;
  }

  public void forceCreation() {
  }
}
