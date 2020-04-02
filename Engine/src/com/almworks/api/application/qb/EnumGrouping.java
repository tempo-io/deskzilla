package com.almworks.api.application.qb;

import com.almworks.api.application.ItemKeyGroup;
import com.almworks.api.application.ResolvedItem;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public interface EnumGrouping<G extends ItemKeyGroup> {
  @Nullable
  G getGroup(ResolvedItem item);

  @NotNull
  Comparator<G> getComparator();

  @NotNull
  String getDisplayableName();
}
