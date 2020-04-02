package com.almworks.api.database;

import java.util.List;


/**
 * Remotely Controlled, Branching Artifact
 *
 * @author sereda
 */
public interface RCBArtifact extends ArtifactExtension, RCBBranching, RCBReincarnator {
  /**
   * Returns a collection of all revisions that belong to this artifact, in no particular order.
   * NB: Returns revisions that are buried! (Reincarnated over)
   * @param recipient
   */
  List<Revision> getCompleteRevisionsList(List<Revision> recipient);
}
