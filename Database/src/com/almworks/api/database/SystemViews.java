package com.almworks.api.database;

import com.almworks.api.database.typed.ArtifactType;
import org.almworks.util.TypedKey;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface SystemViews {
  ArtifactView getRootView();

  /**
   * @return a view that sees user objects (not system objects), that are not deleted.
   *         System objects are all objects created during big bang.
   */
  ArtifactView getUserView();

//  <T extends TypedArtifact> ArtifactView getTypedView(Class<T> typedClass);
  ArtifactView getTypedView(TypedKey<ArtifactType> type);

  ArtifactView getTypedView(ArtifactPointer type);
}
