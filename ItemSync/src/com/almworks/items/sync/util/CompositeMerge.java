package com.almworks.items.sync.util;

import com.almworks.items.sync.*;
import org.almworks.util.ArrayUtil;

import java.util.Collection;

public class CompositeMerge implements ItemAutoMerge {
  private final ItemAutoMerge[] myMerges;

  public CompositeMerge(ItemAutoMerge ... merges) {
    myMerges = ArrayUtil.arrayCopy(merges);
  }

  public CompositeMerge(Collection<? extends ItemAutoMerge> merges) {
    myMerges = merges.toArray(new ItemAutoMerge[merges.size()]);
  }

  @Override
  public void preProcess(ModifiableDiff local) {
    for (ItemAutoMerge merge : myMerges) merge.preProcess(local);
  }

  @Override
  public void resolve(AutoMergeData data) {
    for (ItemAutoMerge merge : myMerges) merge.resolve(data);
  }
}
