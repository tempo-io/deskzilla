package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.items.api.DBReader;
import com.almworks.util.threads.CanBlock;
import org.jetbrains.annotations.*;

public interface RemoteSearchable {
  /**
   * Builds element for boolean chart creation
   */
  @Nullable
  @CanBlock
  BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection);
}
