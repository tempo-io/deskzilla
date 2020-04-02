package com.almworks.api.database;

import com.almworks.api.database.typed.AttributeImage;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RevisionImage extends ArtifactPointer {
  boolean isDeleted();

  Map<AttributeImage, Value> getData();

  <T extends RevisionImage> T getTyped(Class<T> typedImageClass);
}
