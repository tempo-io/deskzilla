package com.almworks.database;

import com.almworks.api.database.RevisionAccess;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RootViewProvider {
  AbstractArtifactView getRootView(RevisionAccess strategy);
}
