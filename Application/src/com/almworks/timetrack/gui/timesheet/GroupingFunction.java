package com.almworks.timetrack.gui.timesheet;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.LoadedItem;
import org.jetbrains.annotations.*;

public interface GroupingFunction {
  @NotNull
  ItemKey getGroupValue(LoadedItem item);
}
