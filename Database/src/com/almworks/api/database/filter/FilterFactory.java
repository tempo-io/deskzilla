package com.almworks.api.database.filter;

import com.almworks.api.database.Filter;

import java.util.Map;

public interface FilterFactory {
  String getFilterName();

  Map<String, FilterParameterType> getFilterParameters();

  Filter buildFilter(Map<String, Object> arguments);
}
