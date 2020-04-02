package com.almworks.api.database;

import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface RevisionChain {
  Artifact getArtifact();

  /**
   * @return first revision - must never return null!
   */
  Revision getFirstRevision();

  long getKey();

  Revision getLastRevisionOrNull(WCN wcn);

  Revision getLastRevision(WCN wcn);

  Revision getLastRevision();

//  ScalarModel<Revision> getLastRevisionModel();

  boolean containsRevision(Revision revision);

  /**
   * Returns the same revision, but on this chain. Returns NULL if revision is not on this chain.
   */
  Revision getRevisionOnChainOrNull(Revision revision);

  List<Revision> getCompleteRevisionsList(List<Revision> recipient);

  /**
   * @see Revision#getOrder() 
   */
  long getRevisionOrder(Revision revision);
}
