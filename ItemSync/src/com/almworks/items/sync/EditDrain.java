package com.almworks.items.sync;

public interface EditDrain extends DBDrain {
  /**
   * Discard local changes for the item and whole slave subtree
   * @return true if the whole subtree is successfully discarded (is in sync state now).<br>
   * false means that discard failed and no item is changed
   */
  boolean discardChanges(long item);

  /**
   * Marks the item manually merged - current conflict values becomes new base
   * @return creator to write merge result to trunk
   */
  ItemVersionCreator markMerged(long item);
}
