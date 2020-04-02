package com.almworks.api.database;

import com.almworks.api.database.typed.Attribute;
import org.almworks.util.TypedKey;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface RevisionCreator extends ArtifactPointer {
  void deleteObject();

  boolean isBuilt();

  boolean isChanged(TypedKey<Attribute> systemAttribute);

  boolean isChanged(ArtifactPointer attribute);

  boolean isNew();

  /**
   * @return true if changed
   */
  boolean setValue(TypedKey<Attribute> systemAttribute, Object value);

  /**
   * @return true if changed
   */
  boolean setValue(ArtifactPointer attribute, Object value);

  /**
   * @return true if changed
   */
  boolean unsetValue(TypedKey<Attribute> systemAttribute);

  /**
   * @return true if changed
   */
  boolean unsetValue(ArtifactPointer attribute);

  Revision asRevision();

  boolean isEmpty();

  /**
   * Revision is created even if no changes are done.
   */
  void forceCreation();

  Value getChangingValue(ArtifactPointer attribute);
}
