package com.almworks.api.database.typed;

import java.util.Collection;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ArtifactTypeImage extends NamedArtifactImage, ArtifactType {
  Collection<AttributeImage> getRequiredAttributeImages();

  Collection<AttributeImage> getOptionalAttributeImages();

  Collection<AttributeImage> getAllAttributeImages();
}
