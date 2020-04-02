package com.almworks.api.database.typed;

import com.almworks.api.database.RevisionImage;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface TypedArtifactImage extends RevisionImage, TypedArtifact {
  ArtifactTypeImage getTypeImage();
}
