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
public interface RevisionRefined extends RevisionInternals {
  Value getValue(ArtifactPointer pointer);

  Atom getAtom();

  RevisionChain getChain();

  WCN getWCN();

  long getKey();

  Revision getPrevRevision();

  MapIterator<ArtifactPointer, Value> iterator();

  Map<ArtifactPointer,Value> getChanges();

  RevisionIterator getRevisionIterator();
}
