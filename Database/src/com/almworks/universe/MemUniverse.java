package com.almworks.universe;

import com.almworks.api.universe.*;
import org.almworks.util.Collections15;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class MemUniverse implements Universe {
  protected final State myState = new State();

  public long getUCN() {
    return myState.getUCN();
  }

  public Atom getAtom(long atomID) {
    return myState.getAtom(atomID);
  }

  public synchronized Index createIndex(IndexInfo indexInfo) {
    IndexInternal index = IndexFactory.createIndex(myState, indexInfo);
    myState.addIndex(index);
    return index;
  }

  public synchronized Index[] getIndices() {
    Collection<IndexInternal> result = myState.getIndices();
    return result.toArray(new Index[result.size()]);
  }

  public synchronized Index[] getIndices(Atom atom) {
    Collection<IndexInternal> all = myState.getIndices();
    List<Index> result = Collections15.arrayList();
    for (IndexInternal index : all) {
      if (index.getInfo().getCondition().isAccepted(atom))
        result.add(index);
    }
    return result.toArray(new Index[result.size()]);
  }

  public Index getIndex(String indexName) {
    return myState.getIndex(indexName);
  }

  public Index getIndex(int indexID) {
    return myState.getIndex(indexID);
  }

  public Index getGlobalIndex() {
    return myState.getGlobalIndex();
  }

  public boolean isDefaultIndexing() {
    return myState.isDefaultIndexing();
  }

  public void setDefaultIndexing(boolean autoIndexing) {
    myState.setDefaultIndexing(autoIndexing);
  }

  public Expansion begin() {
    return new MemExpansionImpl(myState);
  }

  public Map<String, String> getCustomProperties() {
    return Collections15.emptyMap();
  }

  public void setCustomPropertiesIfCreating(Map<String, String> metadata) {
  }
}
