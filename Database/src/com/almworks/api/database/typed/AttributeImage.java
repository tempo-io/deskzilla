package com.almworks.api.database.typed;

import java.util.Comparator;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface AttributeImage extends NamedArtifactImage, Attribute {
  Comparator<AttributeImage> DISPLAY_NAME_COMPARATOR = new Comparator<AttributeImage>() {
    public int compare(AttributeImage o1, AttributeImage o2) {
      return String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayableName(), o2.getDisplayableName());
    }
  };
}
