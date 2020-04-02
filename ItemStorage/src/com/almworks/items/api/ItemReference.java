package com.almworks.items.api;

public interface ItemReference {
  long findItem(DBReader reader);

  ItemReference NO_ITEM = new ItemReference() {
    @Override
    public long findItem(DBReader reader) {
      return 0;
    }
  };

  class Item implements ItemReference {
    private final long myItem;

    public Item(long item) {
      myItem = item;
    }

    @Override
    public long findItem(DBReader reader) {
      return myItem;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof Item)) return false;
      Item other = (Item) obj;
      return myItem == other.myItem;
    }

    @Override
    public int hashCode() {
      return ((int)myItem) ^ ((int)(myItem >> 32));
    }
  }
}
