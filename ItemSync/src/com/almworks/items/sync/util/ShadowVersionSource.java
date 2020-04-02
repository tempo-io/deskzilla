package com.almworks.items.sync.util;

import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.impl.BasicVersionSource;
import org.jetbrains.annotations.*;

public class ShadowVersionSource extends BasicVersionSource {
  private final boolean myConflict;
  private final DBReader myReader;

  ShadowVersionSource(DBReader reader, boolean conflict) {
    myReader = reader;
    myConflict = conflict;
  }

  public static VersionSource conflict(DBReader reader) {
    return new ShadowVersionSource(reader, true);
  }

  public static VersionSource base(DBReader reader) {
    return new ShadowVersionSource(reader, false);
  }

  @NotNull
  @Override
  public DBReader getReader() {
    return myReader;
  }

  @NotNull
  @Override
  public ItemVersion forItem(long item) {
    if (item <= 0) return new IllegalItem(this, item);
    ItemVersion version = getVersion(item);
    return new ItemVersionWrapper(this, version);
  }

  private ItemVersion getVersion(long item) {
    ItemVersion version = null;
    if (myConflict) version = SyncUtils.readConflictIfExists(getReader(), item);
    if (version != null) return version;
    version = SyncUtils.readBaseIfExists(getReader(), item);
    if (version == null) version = SyncUtils.readTrunk(getReader(), item);
    return version;
  }
}
