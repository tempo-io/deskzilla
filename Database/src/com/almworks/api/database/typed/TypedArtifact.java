package com.almworks.api.database.typed;

import com.almworks.api.database.ArtifactPointer;
import com.almworks.api.database.Revision;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface TypedArtifact extends ArtifactPointer {
  boolean isValid(Revision revision);

  ArtifactType getType();
}
