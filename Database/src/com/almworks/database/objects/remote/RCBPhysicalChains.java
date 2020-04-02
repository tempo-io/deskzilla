package com.almworks.database.objects.remote;

import com.almworks.api.database.RevisionChain;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
final class RCBPhysicalChains {
  private final RevisionChain myMainChain;
  private final long myIncarnationUcn;
  private final List<RevisionChain> myLocalChains;
  private final int myIncarnations;
  private final long myLastBinderAtomID;
  private final Map<Integer, RevisionChain> myBuriedChains;

  public RCBPhysicalChains(RevisionChain mainChain, List<RevisionChain> localPhysicalChains, int incarnations,
    long lastBinderAtomID, Map<Integer, RevisionChain> buriedChains, long incarnationUcn)
  {
    myMainChain = mainChain;
    myIncarnationUcn = incarnationUcn;
    myLocalChains = localPhysicalChains;
    myIncarnations = incarnations;
    myLastBinderAtomID = lastBinderAtomID;
    myBuriedChains = buriedChains;
  }

  public long getIncarnationUcn() {
    return myIncarnationUcn;
  }

  public RevisionChain getMainChain() {
    return myMainChain;
  }

  public List<RevisionChain> getLocalChains() {
    return myLocalChains == null ? Collections15.<RevisionChain>emptyList() : myLocalChains;
  }

  public RevisionChain getLastLocalChain() {
    return myLocalChains != null && myLocalChains.size() > 0 ? myLocalChains.get(0) : null;
  }

  public int getIncarnations() {
    return myIncarnations;
  }

  public RevisionChain getFirstLocalChain() {
    if (myLocalChains == null)
      return null;
    int n = myLocalChains.size();
    return n > 0 ? myLocalChains.get(n - 1) : null;
  }

  public long getLastBinderAtomID() {
    return myLastBinderAtomID;
  }

  public RevisionChain getBuriedChain(Integer incarnation) {
    return myBuriedChains == null ? null : myBuriedChains.get(incarnation);
  }

  public Collection<RevisionChain> getBuriedChains() {
    return myBuriedChains == null ? Collections15.<RevisionChain>emptyCollection() : myBuriedChains.values();
  }
}
