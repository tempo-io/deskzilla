package com.almworks.items.sync;

import com.almworks.items.api.DBReader;
import com.almworks.items.api.ItemReference;

public interface ItemProxy extends ItemReference {
  ItemProxy NULL = new ItemProxy() {
    @Override
    public long findOrCreate(DBDrain drain) {
      return 0;
    }

    @Override
    public long findItem(DBReader reader) {
      return 0;
    }
  };

  long findOrCreate(DBDrain drain);
}
