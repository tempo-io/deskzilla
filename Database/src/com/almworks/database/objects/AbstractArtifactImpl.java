package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyedObject;

public abstract class AbstractArtifactImpl extends LongKeyedObject {
  protected final Basis myBasis;

  protected AbstractArtifactImpl(Basis basis, long atomKey) {
    super(atomKey);
    myBasis = basis;
  }

  public abstract RevisionChain getChain(RevisionAccess strategy);

  public abstract boolean isAccessStrategySupported(RevisionAccess strategy);

  public abstract boolean containsRevision(Revision revision);

  public abstract boolean isValid();
}
