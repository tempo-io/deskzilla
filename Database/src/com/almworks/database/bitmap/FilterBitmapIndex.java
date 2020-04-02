package com.almworks.database.bitmap;

import com.almworks.api.database.Revision;
import com.almworks.api.database.RevisionAccess;
import com.almworks.database.Basis;
import com.almworks.database.filter.SystemFilter;
import util.concurrent.ReadWriteLock;

class FilterBitmapIndex extends AbstractBitmapIndex {
  public FilterBitmapIndex(RevisionAccess strategy, SystemFilter systemFilter, ReadWriteLock updateLock, Basis basis) {
    super(strategy, systemFilter, updateLock, basis);
  }

  protected void updateIndexWithCorrectStrategy(Revision revision) {
    if (revision == null)
      return;
    long atomID = myBasis.getAtomID(revision);
    SystemFilter filter = getSystemFilter();
    boolean accept = filter.accept(revision);
    updateBit(atomID, accept);
  }
}
