package com.almworks.database.bitmap;

import com.almworks.database.filter.FilterType;
import org.jetbrains.annotations.*;
import util.external.BitSet2;

abstract class BINode {
  private final FilterType myType;

  public BINode(FilterType type) {
    myType = type;
  }

  public final FilterType getType() {
    return myType;
  }

  public abstract BitSet2 getBits(BitSet2 mask) throws InterruptedException;

  public abstract boolean canApplyQuick();

  @Nullable
  public abstract BitSet2 applyQuick(BitSet2 sourceSet) throws InterruptedException;


  public static final class All extends BINode {
    public static final All INSTANCE = new All();

    public All() {
      super(FilterType.LEAF);
    }

    public BitSet2 getBits(BitSet2 mask) {
      return mask;
    }

    public boolean canApplyQuick() {
      return true;
    }

    public BitSet2 applyQuick(BitSet2 sourceSet) {
      return sourceSet;
    }
  }

  public static final class None extends BINode {
    private static final BitSet2 EMPTY = new BitSet2();
    public static final None INSTANCE = new None();

    public None() {
      super(FilterType.LEAF);
    }

    public BitSet2 getBits(BitSet2 mask) {
      return EMPTY;
    }

    public boolean canApplyQuick() {
      return true;
    }

    public BitSet2 applyQuick(BitSet2 sourceSet) {
      return EMPTY;
    }
  }
}
