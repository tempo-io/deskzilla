package com.almworks.items.impl.dbadapter;

import com.almworks.integers.LongIterable;
import com.almworks.integers.LongList;
import com.almworks.util.Break;

public interface ItemCache extends PrioritizedListenerSupport<ItemCache.Listener> {
  <T> void visit(LongIterable items, Visitor<T> visitor);

  <T> void visitAll(Visitor<T> visitor);

  void start();

  void stop();

  <T> T visit(long item, Visitor<T> visitor);

  interface Listener {
    void onReload(ItemCache source);

    void onUpdate(ItemCache source, LongList added, LongList removed, LongList changed);
  }


  /**
   * must not call any ItemCache's method, as visitor's methods are called under lock
   * all locks used by implementing methods must be well controlled
   */
  interface Visitor<T> {
    T visitItem(ItemAccessor itemAccessor, T visitPayload) throws Break;
  }
}
