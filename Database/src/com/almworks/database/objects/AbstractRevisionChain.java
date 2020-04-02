package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyed;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;

import java.util.List;

public abstract class AbstractRevisionChain extends LongKeyed implements RevisionChain {
  protected final Basis myBasis;
  private volatile CachedLastRevision myCachedLastRevision = null; //todo here optimize

  protected AbstractRevisionChain(Basis basis, long atomKey) {
    super(atomKey);
    assert basis != null;
    myBasis = basis;
  }

  public Artifact getArtifact() {
    return myBasis.getArtifactByChainKey(key());
  }

  public long getKey() {
    return key();
  }

  public Revision getLastRevisionOrNull(WCN beforeWCN) {
    return getLastRevision(beforeWCN, true);
  }

  public Revision getLastRevision(WCN beforeWCN) {
    return getLastRevision(beforeWCN, false);
  }

  private Revision getLastRevision(WCN beforeWCN, boolean tolerateNull) {
    Revision last = getLastRevision();
    if (last == null)
      return nullOrException(tolerateNull, "has no revisions");

    // optimization
    if (WCN.LATEST == beforeWCN || myBasis.getCurrentWCN().getUCN() <= beforeWCN.getUCN())
      return last;

    if (getFirstRevision().getWCN().compareTo(beforeWCN) >= 0) {
      // object is completely in the future
      return nullOrException(tolerateNull, "is in the future (" + beforeWCN + ")");
    }

    for (; last != null; last = last.getPrevRevision())
      if (last.getWCN().compareTo(beforeWCN) < 0)
        return last;

    return nullOrException(tolerateNull, "cannot find revisions");
  }

  private Revision nullOrException(boolean tolerateNull, String msg) {
    if (!tolerateNull)
      throw new Failure(this + " " + msg);
    return null;
  }

  //  private static DebugDichotomy hitmiss = new DebugDichotomy("chain.hits", "chain.misses", 1000);
  public final Revision getLastRevision() {
    // caching method - getLastRevision() is called too frequently
    long ucn = myBasis.getCurrentWCN().getUCN();

    CachedLastRevision cached = myCachedLastRevision; // volatile access

    // caching will stop on ucn larger than 2^31
    // (wake me from the grave then)
    if (cached != null && cached.myCachedUcnSmall >= ucn && ucn < 0x7FFFFFFFL)
      return cached.myRevision;

    long currentUCN = 0;
    synchronized (this) {    // deadlock here!
      currentUCN = myBasis.getCurrentWCN().getUCN(); // ucn may have changed while we waited for the lock
      if (myCachedLastRevision != null && myCachedLastRevision.myCachedUcnSmall >= ucn && ucn < 0x7FFFFFFFL)
        return myCachedLastRevision.myRevision;
    }

    // call doGetLastRevision without lock!
    Revision revision = doGetLastRevision();
    if (revision == null)
      throw new Failure(this + " has no revisions");

    synchronized (this) {
      if (myCachedLastRevision == null || myCachedLastRevision.myCachedUcnSmall < currentUCN || ucn >= 0x7FFFFFFFL) {
        myCachedLastRevision = new CachedLastRevision((int)currentUCN, revision);
        return revision;
      } else {
        return myCachedLastRevision.myRevision;
      }
    }
  }

  public boolean containsRevision(Revision revision) {
    Revision r = getLastRevisionOrNull(WCN.LATEST);
    while (r != null) {
      if (r.equals(revision))
        return true;
      r = r.getPrevRevision();
    }
    return false;
  }

  // todo remove
  public Revision __getLastRevisionWithoutCaching() {
    return doGetLastRevision();
  }

  protected abstract Revision doGetLastRevision();

  public abstract RevisionIterator getRevisionIterator();

  public Revision getRevisionOnChainOrNull(Revision revision) {
    if (containsRevision(revision))
      return myBasis.getRevision(myBasis.getAtomID(revision), getRevisionIterator());
    else
      return null;
  }

  public List<Revision> getCompleteRevisionsList(List<Revision> recipient) {
    if (recipient == null)
      recipient = Collections15.arrayList();
    Revision revision = getLastRevisionOrNull(WCN.LATEST);
    while (revision != null) {
      recipient.add(revision);
      revision = revision.getPrevRevision();
    }
    return recipient;
  }

  public long getRevisionOrder(Revision revision) {
    return revision.getWCN().getUCN();
  }


  private static final class CachedLastRevision {
    private final int myCachedUcnSmall;
    private final Revision myRevision;

    public CachedLastRevision(int cachedUcnSmall, Revision revision) {
      myCachedUcnSmall = cachedUcnSmall;
      myRevision = revision;
    }
  }
}
