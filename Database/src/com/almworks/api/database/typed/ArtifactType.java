package com.almworks.api.database.typed;

import java.util.Collection;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ArtifactType extends NamedArtifact {
  ArtifactType getSuperType();

  Collection<Attribute> getRequiredAttributes();

  Collection<Attribute> getOptionalAttributes();

  Collection<Attribute> getAllAttributes();
}
