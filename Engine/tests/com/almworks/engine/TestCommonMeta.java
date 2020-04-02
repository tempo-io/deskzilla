package com.almworks.engine;

import com.almworks.api.engine.Engine;
import com.almworks.items.api.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;

public class TestCommonMeta {
  private static final DBNamespace NS = Engine.NS.subNs("test");
  public final static DBItemType NOTE = NS.type("note", "Note");
  public final static DBItemType CONNECTION = NS.type("connectionType", "TestConnection");

  public final static DBAttribute<Boolean> IS_NEW = NS.bool("isNew", "Is new?", false);
  public final static DBAttribute<String> AUTHOR = NS.string("author", "Author", true);
  public final static DBAttribute<String> TEXT = NS.string("text", "Text", true);
  public final static DBAttribute<Integer> ID = NS.integer("id", "ID", false);

  static {
    NOTE.initialize(SyncAttributes.IS_PRIMARY_TYPE, true);
  }

  // cache of materialized types and attributes
  public long typeNote;
  public long typeConnection;

  public long atIsNew;
  public long atAuthor;
  public long atText;
  public long atId;

  public void materialize(DBWriter writer) {
    typeNote = writer.materialize(NOTE);
    typeConnection = writer.materialize(CONNECTION);

    atIsNew = writer.materialize(IS_NEW);
    atAuthor = writer.materialize(AUTHOR);
    atText = writer.materialize(TEXT);
    atId = writer.materialize(ID);
  }
}
