package com.almworks.api.database;

import com.almworks.util.Pair;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.detach.Detach;

import java.util.*;

/**
 * This interface provides view to an object Set.
 *
 * @author sereda
 */

public interface ArtifactView {
  // deprecated because it requires advanced use. see HintedArtifactView
  @Deprecated()
  Detach addListenerFuture(ThreadGate callbackGate, ArtifactListener listener);

  Detach addListener(ThreadGate callbackGate, WCN.Range range, ArtifactListener listener);

  Filter getFilter();

  ArtifactView filter(Filter filter);

  ArtifactView filter(Filter filter, WCN.Range range);

  ArtifactView changeStrategy(RevisionAccess strategy);

  Set<Revision> filter(Set<Revision> source);

  Snapshot takeSnapshot();

  List<Revision> getAllArtifacts();

  /**
   * controller must return true if the operation may continue. controller also may count revisions.
   */
  List<Revision> getAllArtifacts(Function<Collection<Revision>, Boolean> controller);

  int count();

  Pair<WCN, Integer> countWithWCN();

  Revision queryLatest(Filter filter, RevisionAccess strategy);

  Revision queryLatest(Filter filter);

  Revision queryLatest();

  WCN.Range getRange();

  RevisionAccess getStrategy();

  Detach addListenerPast(ThreadGate callbackGate, ArtifactListener listener);

  void setDebugName(String debugName);

  boolean isCountQuicklyAvailable();

  /**
   * Returns true if revision is visible in this view, i.e. it would have been returned from the "past" if it were
   * a last artifact revision.
   *
   * @param strictStrategy if true, make sure the revision is on this view's strategy chain.
   */
  boolean isVisible(Revision revision, boolean strictStrategy);

  void callWhenPassed(WCN wcn, ThreadGate gate, Runnable runnable);

  /**
   * Performs index verification (delayed)
   * @param reportTo
   */
  void verify(Procedure<Boolean> reportTo);
}
