package com.almworks.items.util;

import com.almworks.items.api.*;

public class SyncAttributes {
  private static final DBNamespace NS = Database.NS.subModule("sync");
  public static final DBAttribute<Long> CONNECTION = DBAttribute.Long(NS.attr("connection"), "Connection");
  public static final DBAttribute<Boolean> IS_PRIMARY_TYPE = DBAttribute.Bool(NS.attr("primary"), "Primary?");

  public static final DBAttribute<Integer> ITEM_DOWNLOAD_STAGE = DBAttribute.Int(NS.attr("downloadStage"), "Download Stage");
  public static final DBAttribute<Boolean> IS_PROTOTYPE = DBAttribute.Bool(NS.attr("prototype"), "Prototype?");
  public static final DBAttribute<Boolean> EXISTING = DBAttribute.Bool(NS.attr("existing"), "Existing?");

  public static final DBAttribute<AttributeMap> BASE_SHADOW =     Shadow(NS.attr("base"), "Base Shadow");
  public static final DBAttribute<AttributeMap> CONFLICT_SHADOW = Shadow(NS.attr("conflict"), "Conflict Shadow");
  public static final DBAttribute<Boolean> SHADOWABLE = DBAttribute.Bool(NS.attr("shadowable"), "Shadowable?");
  public static final DBAttribute<Boolean> INVISIBLE = DBAttribute.Bool(NS.attr("invisible"), "Invisible?");
  public static final DBAttribute<byte[]> CHANGE_HISTORY =
    DBAttribute.Scalar(NS.attr("changeHistory"), "Change History", byte[].class);


  public static boolean isShadowable(DBAttribute<?> a, DBReader dbAccess) {
    long materialized = dbAccess.findMaterialized(a);
    if (materialized == 0)
      return false;
    return isShadowable(dbAccess, materialized);
  }

  public static void initShadowable(DBAttribute<?>... attrs) {
    for (final DBAttribute<?> attr : attrs) {
      attr.initialize(SHADOWABLE, true);
    }
  }

  public static boolean isShadowable(DBReader reader, long attr) {
    Boolean value = SHADOWABLE.getValue(attr, reader);
    return value != null && value;
  }

  public static DBAttribute<AttributeMap> Shadow(String id, String name) {
    return DBAttribute.Scalar(id, name, AttributeMap.class);
  }

  static {
    initShadowable(INVISIBLE);
  }
}
