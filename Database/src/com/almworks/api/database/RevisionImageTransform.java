package com.almworks.api.database;

import com.almworks.api.database.typed.AttributeImage;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface RevisionImageTransform <K, V> {
  K transformAttribute(AttributeImage attribute, Value value);

  V transformValue(AttributeImage attribute, Value value);
}
