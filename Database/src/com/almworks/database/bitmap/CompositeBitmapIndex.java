package com.almworks.database.bitmap;

import com.almworks.api.database.Filter;
import com.almworks.api.database.RevisionAccess;
import com.almworks.database.filter.FilterType;
import com.almworks.database.filter.SystemFilter;
import util.external.BitSet2;


public class CompositeBitmapIndex {
  private final BINodeComposite myRootNode;

  private CompositeBitmapIndex(BINodeComposite rootNode) {
    myRootNode = rootNode;
  }

  public static CompositeBitmapIndex create(BitmapIndexManager indexManager, Filter filter, RevisionAccess strategy) throws IncompatibleFilter {
    BINode filterTree = createTreeFromFilter(indexManager, filter, strategy);
    BINodeComposite node;
    if (filterTree.getType() == FilterType.LEAF) {
      node = new BINodeComposite(FilterType.AND);
      node.add(filterTree);
      node.setBuilt();
    } else {
      node = (BINodeComposite) filterTree;
    }
    return new CompositeBitmapIndex(node);
  }

  public void rebuildCorruptIndexes(BitmapIndexManager indexManager) throws InterruptedException {
    rebuildLeafIndexes(indexManager, myRootNode);
  }

  private void rebuildLeafIndexes(BitmapIndexManager indexManager, BINode node) throws InterruptedException {
    if (node instanceof BINodeLeaf) {
      indexManager.rebuildIndex(((BINodeLeaf) node).getIndex());
    } else if (node instanceof BINodeComposite) {
      BINode[] children = ((BINodeComposite) node).getChildren();
      for (int i = 0; i < children.length; i++) {
        BINode child = children[i];
        rebuildLeafIndexes(indexManager, child);
      }
    }
  }

//private static final DebugDichotomy __stats = new DebugDichotomy("cbi:quick", "cbi:full", 100);

  public BitSet2 filterBits(BitSet2 sourceSet) throws InterruptedException {
    if (myRootNode.canApplyQuick()) {
      BitSet2 result = myRootNode.applyQuick(sourceSet);
      if (result != null) {
//        __stats.a();
        return result;
      } else {
        assert false : myRootNode;
        // betrayed
        // safe to continue though
      }
    }
    
    BitSet2 mask = getMask(sourceSet.size());
    BitSet2 bits = myRootNode.getBits(mask);
//    __stats.b();
    return sourceSet.modifiable().and(bits);
  }

  private static BINode createTreeFromFilter(BitmapIndexManager indexManager, Filter filter, RevisionAccess strategy) throws IncompatibleFilter {
    if (filter == Filter.ALL)
      return BINode.All.INSTANCE;
    if (filter == Filter.NONE)
      return BINode.None.INSTANCE;

    SystemFilter systemFilter = getSystemFilter(filter);
    if (systemFilter.isComposite()) {
      BINodeComposite result = new BINodeComposite(systemFilter.getType());
      Filter[] children = systemFilter.getChildren();
      for (int i = 0; i < children.length; i++) {
        Filter child = children[i];
        BINode subTree = createTreeFromFilter(indexManager, child, strategy);
        result.add(subTree);
      }
      result.setBuilt();
      return result;
    } else {
      assert systemFilter.getType() == FilterType.LEAF;
      AbstractBitmapIndex index = indexManager.getBitmapIndex(systemFilter, strategy);
      return new BINodeLeaf(index);
    }
  }

  private static BitSet2 getMask(int bits) {
    BitSet2 bitSet = new BitSet2(bits);
    bitSet.set(0, bits);
    return bitSet;
  }

  private static SystemFilter getSystemFilter(Filter filter) throws IncompatibleFilter {
    if (!(filter instanceof SystemFilter))
      throw new IncompatibleFilter(filter);
    return (SystemFilter) filter;
  }

  public static boolean hasAllIndices(BitmapIndexManager indexManager, Filter filter, RevisionAccess strategy) {
    try {
      if (filter == Filter.ALL || filter == Filter.NONE)
        return true;
      SystemFilter systemFilter = getSystemFilter(filter);
      if (systemFilter.isComposite()) {
        Filter[] children = systemFilter.getChildren();
        for (Filter child : children) {
          if (!hasAllIndices(indexManager, child, strategy))
            return false;
        }
        return true;
      } else {
        assert systemFilter.getType() == FilterType.LEAF;
        return indexManager.hasBitmapIndex(systemFilter, strategy);
      }
    } catch (IncompatibleFilter e) {
      return false;
    }
  }

  public static final class IncompatibleFilter extends Exception {
    public IncompatibleFilter(Filter filter) {
      super(String.valueOf(filter));
    }
  }
}
