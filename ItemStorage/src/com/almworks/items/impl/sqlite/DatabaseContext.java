package com.almworks.items.impl.sqlite;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBException;
import com.almworks.items.impl.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Map;

public class DatabaseContext {
  private final DBConfiguration myConfiguration;
  private final Map<DBAttribute, AttributeAdapter> myAttributeAdapters = Collections15.hashMap();

  public DatabaseContext(DBConfiguration configuration) {
    myConfiguration = configuration;
  }

  public DBConfiguration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public AttributeAdapter getAttributeAdapter(DBAttribute<?> attribute) {
    synchronized (myAttributeAdapters) {
      AttributeAdapter t = myAttributeAdapters.get(attribute);
      if (t == null) {
        ScalarValueAdapter scalarAdapter = myConfiguration.getScalarValueAdapter(attribute.getScalarClass());
        if (scalarAdapter == null) {
          throw new DBException("cannot use attribute with " + attribute.getScalarClass());
        }
        t = AttributeAdapter.create(attribute, scalarAdapter);
        myAttributeAdapters.put(attribute, t);
      }
      return t;
    }
  }
}
