package com.almworks.items.sync.impl;

import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * @author dyoma
 */
public class BranchSource extends BasicVersionSource {
  private static final TypedKey<Map<Branch, BranchSource>> BRANCHES = TypedKey.create("branchSources");
  private final DBReader myReader;
  private final Branch myBranch;

  private BranchSource(DBReader reader, Branch branch) {
    myReader = reader;
    myBranch = branch;
  }

  public static BranchSource instance(DBReader reader, Branch branch) {
    Map cache = reader.getTransactionCache();
    Map<Branch, BranchSource> map = BRANCHES.getFrom(cache);
    if (map == null) {
      map = Collections15.hashMap();
      BRANCHES.putTo(cache, map);
    }
    BranchSource source = map.get(branch);
    if (source == null) {
      source = new BranchSource(reader, branch);
      map.put(branch, source);
    }
    return source;
  }

  @NotNull
  @Override
  public DBReader getReader() {
    return myReader;
  }

  @NotNull
  @Override
  public ItemVersion forItem(long item) {
    return BranchUtil.instance(getReader()).readItem(item, myBranch);
  }
}
