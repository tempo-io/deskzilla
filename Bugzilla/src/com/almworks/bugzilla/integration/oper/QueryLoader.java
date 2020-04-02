package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.util.progress.Progress;
import org.jetbrains.annotations.*;

import java.util.List;

public interface QueryLoader {
  /** @param urlSuffix does not include base URL */
  @NotNull
  List<BugInfoMinimal> load(@NotNull String urlSuffix, @Nullable Progress progress) throws ConnectorException;
}
