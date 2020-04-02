package com.almworks.database.bitmap;

import com.almworks.database.filter.FilterType;
import org.almworks.util.Collections15;
import org.almworks.util.Failure;
import util.external.BitSet2;

import java.util.List;

public class BINodeComposite extends BINode {
  private final List<BINode> myChildren = Collections15.arrayList();
  private boolean myBuilt = false;

  public BINodeComposite(FilterType type) {
    super(type);
  }

  public synchronized void add(BINode node) {
    assert !myBuilt : this;
    myChildren.add(node);
  }

  public synchronized void setBuilt() {
    myBuilt = true;
  }

  public synchronized BitSet2 getBits(BitSet2 mask) throws InterruptedException {
    FilterType type = getType();
    BitSet2 bits = null;
    BINodeOperation operation = getOperation(type);
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      BINode node = myChildren.get(i);
      BitSet2 b = node.getBits(mask);
      bits = operation.consume(bits, b, mask);
    }
    return operation.finish(bits, mask);
  }

  private static BINodeOperation getOperation(FilterType type) {
    if (type == FilterType.AND)
      return BINodeAnd.INSTANCE;
    else if (type == FilterType.OR)
      return BINodeOr.INSTANCE;
    else if (type == FilterType.EXCLUDE)
      return BINodeNot.INSTANCE;
    else
      throw new Failure();
  }

  public synchronized BINode[] getChildren() {
    return myChildren.toArray(new BINode[myChildren.size()]);

  }

  public boolean canApplyQuick() {
    FilterType type = getType();
    if (type != FilterType.AND)
      return false;
    for (BINode child : myChildren) {
      if (!child.canApplyQuick()) {
        return false;
      }
    }
    return true;
  }

  public BitSet2 applyQuick(BitSet2 sourceSet) throws InterruptedException {
    FilterType type = getType();
    if (type != FilterType.AND)
      return null;
    BitSet2 result = sourceSet;
    for (int i = myChildren.size() - 1; i >= 0; i--) {
      BINode node = myChildren.get(i);
      result = node.applyQuick(result);
      if (result == null) {
        break;
      }
    }
    return result;
  }
}
