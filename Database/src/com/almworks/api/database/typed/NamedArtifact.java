package com.almworks.api.database.typed;

import org.jetbrains.annotations.*;


public interface NamedArtifact extends TypedArtifact {
  @NotNull
  String getName();

  String getDisplayableName();

  Attribute attributeName();
}
