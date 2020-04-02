package com.almworks.database.objects.remote;

import com.almworks.api.database.RevisionChain;
import com.almworks.api.universe.*;
import com.almworks.database.Basis;
import com.almworks.database.objects.PhysicalRevisionIterator;
import com.almworks.database.schema.Schema;
import org.jetbrains.annotations.*;

import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
class RCBUtil {
  static boolean isClosed(Basis basis, RevisionChain localChain, RCBHelperInterface artifact) {
    return getCloseLink(basis, localChain, artifact) != -1;
  }

  static long getCloseLink(Basis basis, RevisionChain localChain, RCBHelperInterface artifact) {
    return seekLink(basis, localChain, artifact, Schema.INDEX_RCB_ENDLINK_LC, Schema.KA_RCB_LINK_ENDLOCALBRANCH);
  }

  static long getOpenLink(Basis basis, RevisionChain localChain, RCBHelperInterface artifact) {
    return seekLink(basis, localChain, artifact, Schema.INDEX_RCB_STARTLINK_LC, Schema.KA_RCB_LINK_BEGINLOCALBRANCH);
  }

  private static long seekLink(Basis basis, RevisionChain localChain, RCBHelperInterface artifact, String indexName,
    LongJunctionKey linkKey)
  {
    Index index = basis.getIndex(indexName);
    long chainID = basis.getAtomID(localChain);
    Iterator<Atom> ii = index.search(chainID);
    while (ii.hasNext()) {
      Atom atom = ii.next();
      if (atom.getLong(Schema.KA_RCB_LINK_LOCALCHAIN) != chainID)
        return -1;
      long mainChain = atom.getLong(linkKey);
      if (artifact.isReincarnating()) {
        if (artifact.isReincarnating(basis.getRevision(mainChain, PhysicalRevisionIterator.INSTANCE))) {
          // hit the relink while reincarnation is still in progress
          continue;
        }
      }
      return mainChain;
    }
    return -1;
  }

  @Nullable
  public static RevisionChain getLastOpenLocalChain(Basis basis, RCBPhysicalChains physicalChains,
    RCBHelperInterface artifact)
  {
    RevisionChain chain = physicalChains.getLastLocalChain();
    if (chain == null)
      return null;
    if (isClosed(basis, chain, artifact))
      return null;
    return chain;
  }
}

