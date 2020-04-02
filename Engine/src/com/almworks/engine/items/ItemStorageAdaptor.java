package com.almworks.engine.items;

import com.almworks.api.engine.Engine;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPNotNull;
import com.almworks.items.dp.DPReferredBy;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Collections15;

import java.util.Arrays;
import java.util.List;

/**
 * Contains utility methods needed for Engine to work with ItemStorage.
 * */
public class ItemStorageAdaptor {
  private ItemStorageAdaptor() {}

  /**
   * Wraps queries for most of the cases where queries are needed in Engine-based applications.<br>
   * Wrapping is needed to instill Engine-specific invariants into the application's database views.
   * */
  public static BoolExpr<DP> wrapExpr(BoolExpr<DP> expr) {
    return Engine.VALID_ITEM.and(expr);
  }

  public static BoolExpr<DP> dpReferredBy(DBAttribute<Long> attrPropagatingChange, BoolExpr<DP> refereeQuery) {
    return DPReferredBy.create(attrPropagatingChange, wrapExpr(refereeQuery));
  }

  public static BoolExpr<DP> modified(DBAttribute<Long> ... masterAttributes) {
    return modified(Arrays.asList(masterAttributes));
  }

  public static BoolExpr<DP> modified(Iterable<DBAttribute<Long>> masterAttributes) {
    return hasShadow(SyncSchema.BASE, masterAttributes);
  }

  public static BoolExpr<DP> inConflict(DBAttribute<Long> ... masterAttributes) {
    return inConflict(Arrays.asList(masterAttributes));
  }

  public static BoolExpr<DP> inConflict(Iterable<DBAttribute<Long>> masterAttributes) {
    return hasShadow(SyncSchema.CONFLICT, masterAttributes);
  }

  public static BoolExpr<DP> hasShadow(DBAttribute<AttributeMap> shadow, Iterable<DBAttribute<Long>> masterAttributes) {
    BoolExpr<DP> hasVersion = DPNotNull.create(shadow);
    List<BoolExpr<DP>> slaves = Collections15.arrayList();
    for (DBAttribute<Long> masterAttribute : masterAttributes)
      slaves.add(dpReferredBy(masterAttribute, hasVersion));
    return hasVersion.or(BoolExpr.or(slaves));
  }
}
