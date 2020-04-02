package com.almworks.database;

import com.almworks.api.database.typed.TypedArtifact;
import org.almworks.util.TypedKey;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface SystemObjectResolver {
  <T extends TypedArtifact> T getSystemObject(TypedKey<T> artifactKey);
}
