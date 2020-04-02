package com.almworks.api.database.util;

import com.almworks.api.database.RevisionCreator;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Initializer {
  void initialize(RevisionCreator creator);
}
