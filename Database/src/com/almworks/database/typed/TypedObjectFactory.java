package com.almworks.database.typed;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface TypedObjectFactory <T extends TypedArtifact> {
  T initializeTyped(Basis basis, RevisionCreator newObject);

  T loadTyped(Basis basis, Artifact object);

  Class<T> getTypedClass();

  ArtifactPointer getTypeArtifact(Basis basis);
}
