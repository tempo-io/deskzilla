package com.almworks.api.database.util;

import com.almworks.api.database.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class ArtifactPointerAspects extends AspectProviderBase<Revision> {
  protected abstract boolean isMyAspectedArtifact(Revision lastRevision);

  protected final Revision getAspectedObject(Aspected object) {
    if (!(object instanceof ArtifactPointer))
      return null;
    Revision lastRevision = ((ArtifactPointer) object).getArtifact().getLastRevision();
    if (lastRevision == null)
      return null;
    if (!isMyAspectedArtifact(lastRevision))
      return null;
    return lastRevision;
  }
}
