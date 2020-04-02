package com.almworks.items.sync;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.api.DBReader;
import org.jetbrains.annotations.*;

import java.util.List;

public interface VersionSource {
  @NotNull
  DBReader getReader();

  /**
   * Creates ItemVersion for the same type of version
   * @param item item to read
   * @return item version from current branch (or trunk if no version in the branch)<br>
   * Return {@link com.almworks.items.sync.util.IllegalItem illegal item} if item is not positive
   * (doesn't corresponds to existing item)
   */
  @NotNull
  ItemVersion forItem(long item);

  @NotNull
  ItemVersion forItem(DBIdentifiedObject object);

  long findMaterialized(DBIdentifiedObject object);

  @NotNull
  List<ItemVersion> readItems(LongList items);
}
