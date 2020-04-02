package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.api.universe.*;
import com.almworks.database.Basis;
import com.almworks.database.DatabaseInconsistentException;
import com.almworks.database.schema.Schema;
import com.almworks.util.exec.Context;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DBUtil {
  public static RevisionInternals getInternals(Revision revision) {
    if (revision == null)
      return null;
    if (revision instanceof RevisionInternals)
      return ((RevisionInternals) revision);
    throw new IllegalStateException(revision.toString());
  }

  public static RevisionChain getPhysicalChain(final Basis basis, final Atom revisionAtom) {
    while (true) {
      try {
        return getPhysicalChainUnsafe(basis, revisionAtom);
      } catch (DatabaseInconsistentException e) {
        basis.ourConsistencyWrapper.handle(e, -1);
      }
    }
  }

  private static RevisionChain getPhysicalChainUnsafe(Basis basis, Atom revisionAtom)
    throws DatabaseInconsistentException
  {
    long chainID = revisionAtom.getLong(Schema.KA_CHAIN_HEAD);
    if (chainID < 0)
      throw new DatabaseInconsistentException("no chain for " + revisionAtom);
    return basis.getPhysicalChain(chainID);
  }

  public static RevisionChain getPhysicalChainUnsafe(Basis basis, long lastRevisionAtomID)
    throws DatabaseInconsistentException
  {
    Atom atom = basis.ourUniverse.getAtom(lastRevisionAtomID);
    if (atom == null) {
      throw new DatabaseInconsistentException("local db problem: " + lastRevisionAtomID);
    }
    return getPhysicalChainUnsafe(basis, atom);
  }

  public static RevisionChain getPhysicalChain(Basis basis, Revision revision) {
    return getPhysicalChain(basis, getInternals(revision).getAtom());
  }

  public static boolean hasUserContent(Atom atom) {
    Map<Long, Particle> map = atom.copyJunctions();
    for (Iterator<Long> ii = map.keySet().iterator(); ii.hasNext();) {
      if (ii.next().longValue() >= 0)
        return true;
    }
    return false;
  }

  /**
   * Returns true if atom specified by atomID resides earlier on revision chain, before revision.
   */
  public static boolean isEarlierOnChain(Basis basis, Revision revision, long atomID) {
    Atom atom = getInternals(revision).getAtom();
    while (true) {
      if (atomID == atom.getAtomID())
        return true;
      long prev = atom.getLong(Schema.KA_PREV_ATOM);
      if (prev < 0)
        break;
      atom = basis.ourUniverse.getAtom(prev);
    }
    return false;
  }

  public static Atom getLastAtomInPhysicalChain(Basis basis, long chainAtom) throws DatabaseInconsistentException {
    // Here's a problem. We could access the last atom by simply calling Index.searchExact() - index
    // looks up atoms in reverse order of WCN. But there's a possibility that there are more than one
    // atoms within one WCN that belong to a single chain. In that case, we have to look which of them
    // are referred by others with KA_PREV reference.
    //
    // There are very few conditions when the latter is possible. This method looks if it can
    // quickly access the last atom, knowing about specifics of these conditions. If quick retrieval
    // is not possible, it falls back to slow generic method.

    Index index = basis.getIndex(Schema.INDEX_KA_CHAIN_HEAD);
    Iterator<Atom> iterator = index.search(chainAtom);

    // Condition: we have only two atoms.
    Atom atom1 = iterator.hasNext() ? iterator.next() : null;
    if (atom1 == null)
      return null;
    if (atom1.getLong(Schema.KA_CHAIN_HEAD) != chainAtom)
      return null;
    long ucn = atom1.getUCN();
    Atom atom2 = iterator.hasNext() ? iterator.next() : null;
    if (atom2 == null)
      return atom1;
    if (atom2.getUCN() != ucn)
      return atom1; // many return here!
    if (atom2.getLong(Schema.KA_CHAIN_HEAD) != chainAtom)
      return atom1;
    Atom atom3 = iterator.hasNext() ? iterator.next() : null;
    if (atom3 != null && atom3.getUCN() == ucn && atom3.getLong(Schema.KA_CHAIN_HEAD) == chainAtom) {
      Log.warn("RevisionMonitor: falling back to slow getLastAtom() [" + chainAtom + "][" + ucn + "]");
      return getLastAtomSlow(basis, chainAtom);
    }
    assert atom1.getAtomID() != atom2.getAtomID();
    if (atom1.getAtomID() < atom2.getAtomID()) {
      Atom a = atom1;
      atom1 = atom2;
      atom2 = a;
    }
    long ref = atom2.getLong(Schema.KA_PREV_ATOM);
    return ref == atom1.getAtomID() ? atom2 : atom1;
  }

  private static Atom getLastAtomSlow(Basis basis, long chainAtom) throws DatabaseInconsistentException {
    Index index = basis.getIndex(Schema.INDEX_KA_CHAIN_HEAD);
    Iterator<Atom> iterator = index.search(chainAtom);
    Set<Long> referredPrev = Collections15.hashSet();
    SortedMap<Long, Atom> atoms = Collections15.treeMap();
    while (iterator.hasNext()) {
      Atom atom = iterator.next();
      if (atom.getLong(Schema.KA_CHAIN_HEAD) != chainAtom)
        break;
      atoms.put(atom.getAtomID(), atom);
      long prev = atom.getLong(Schema.KA_PREV_ATOM);
      if (prev != -1)
        referredPrev.add(prev);
    }
    atoms.keySet().removeAll(referredPrev);
    if (atoms.size() == 0)
      return null;
    return atoms.get(atoms.lastKey());
  }

  /**
   * Hack: this method is the same as revision.getPrevRevision(), only it skips CLOSURE atom, which never gets
   * updated via view.
   */
  public static Revision getPrevRevisionForView(Revision revision) {
    if (revision == null)
      return null;
    Revision prev = revision.getPrevRevision();
    if (prev == null) {
      Atom atom = getInternals(revision).getAtom();
      long incarnation = Schema.KL_REINCARNATION.get(atom);
      if (incarnation >= 2) {
        // reincarnation
        RCBArtifact rcb = revision.getArtifact().getRCBExtension(false);
        if (rcb != null) {
          RevisionChain buriedChain = rcb.getBuriedChain((int) incarnation - 1);
          if (buriedChain != null) {
            return buriedChain.getLastRevision();
          }
        }
      }
      return null;
    }
    Atom atom = getInternals(prev).getAtom();
    if (Schema.KL_IS_CLOSURE.get(atom) >= 0) {
      prev = prev.getPrevRevision();
    }
    return prev;
  }

  public static void printArtifact(long artifactKey) {
    Workspace workspace = Context.require(Workspace.ROLE);
    Artifact artifact;
    try {
      artifact = workspace.getArtifactByKey(artifactKey);
    } catch (InvalidItemKeyException e) {
      System.out.println("Artifact " + artifactKey + " doesnt exist");
      return;
    }
    printArtifact(artifact);
  }

  public static void printArtifact(ArtifactPointer artifact) {
    Revision revision = artifact.getArtifact().getLastRevision();
    Map<ArtifactPointer, Value> values = Collections15.hashMap();
    while (revision != null) {
      Map<ArtifactPointer, Value> changes = revision.getChanges();
      for (Map.Entry<ArtifactPointer, Value> entry : changes.entrySet()) {
        if (WorkspaceUtils.contains(values.keySet(), entry.getKey())) continue;
        values.put(entry.getKey(), entry.getValue());
      }
      revision = revision.getPrevRevision();
    }
    List<ArtifactPointer> attributes = Collections15.arrayList();
    for (ArtifactPointer pointer : values.keySet()) {
      Attribute attr = pointer.getArtifact().getTyped(Attribute.class);
      attributes.add(attr != null ? attr : pointer);
    }
    Collections.sort(attributes, new Comparator<ArtifactPointer>() {
      public int compare(ArtifactPointer o1, ArtifactPointer o2) {
        String name1 = getName(o1);
        String name2 = getName(o2);
        if (name1 == name2) return 0;
        if (name1 == null) return -1;
        if (name2 == null) return 1;
        return String.CASE_INSENSITIVE_ORDER.compare(name1, name2);
      }

      private String getName(ArtifactPointer pointer) {
        return pointer instanceof Attribute ? ((Attribute) pointer).getDisplayableName() : null;
      }
    });
    for (ArtifactPointer attribute : attributes) {
      Value value = values.get(attribute);
      String name = attribute instanceof Attribute ? ((Attribute) attribute).getDisplayableName() : String.valueOf(attribute.getPointerKey());
      String valueStr = value != null ? value.getValue(String.class) : null;
      if (valueStr == null) valueStr = String.valueOf(value);
      System.out.println(name + " = " + valueStr);
    }
  }
}
