package com.almworks.sumtable;

import com.almworks.api.application.tree.QueryResult;

public interface SummaryTableQueryExecutor {
  void runQuery(QueryResult queryResult, STFilter counter, STFilter column, STFilter row, Integer count, boolean newTab);
}
