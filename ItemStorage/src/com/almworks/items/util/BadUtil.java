package com.almworks.items.util;

import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.List;
import java.util.Set;

public class BadUtil {
  public static final BoolExpr<DP> EXPR_MASTER_REF = BoolExpr.and(
    DPEqualsIdentified.create(DBAttribute.TYPE, DBItemType.ATTRIBUTE),
    DPEquals.create(DBAttribute.PROPAGATING_CHANGE, Boolean.TRUE));

  public static List<DBAttribute<Long>> getMasterAttributes(DBReader reader) {
    List<DBAttribute<Long>> attributes = Collections15.arrayList();
    LongList attrs = reader.query(EXPR_MASTER_REF).copyItemsSorted();
    for (int i = 0; i < attrs.size(); i++) {
      long a = attrs.get(i);
      DBAttribute attribute = getAttribute(reader, a);
      if (attribute == null) continue;
      if (!Long.class.equals(attribute.getScalarClass())) continue;
      if (attribute.getComposition() != DBAttribute.ScalarComposition.SCALAR) continue;
      if (!attribute.isPropagatingChange()) continue;
      attributes.add(attribute);
    }
    return attributes;
  }

  public static DBAttribute<?> getAttribute(DBReader reader, long item) {
    String id = reader.getValue(item, DBAttribute.ID);
    if (id != null) {
      DBAttribute attribute = reader.getAttribute(id);
      if (attribute == null) Log.error("Failed to load attribute " + item + " " + id);
      return attribute;
    }
    Log.error("Missing attribute id " + item);
    return null;
  }


  public static LongList getSlaves(DBReader reader, long item) {
    LongArray slaves = new LongArray();
    collectSlaves(reader, item, slaves, getMasterAttributes(reader));
    return slaves.isEmpty() ? LongList.EMPTY : slaves;
  }

  private static void collectSlaves(DBReader reader, long item, WritableLongList target, List<DBAttribute<Long>> masterRefs) {
    LongList slaves = reader.query(querySlaves(item, masterRefs)).copyItemsSorted();
    for (int i = 0; i < slaves.size(); i++) {
      long slave = slaves.get(i);
      if (target.addSorted(slave)) collectSlaves(reader, slave, target, masterRefs);
    }
  }

  private static BoolExpr.Operation<DP> querySlaves(long artifact, List<DBAttribute<Long>> masterAttributes) {
    BoolExpr<DP>[] exprs = new BoolExpr[masterAttributes.size()];
    for (int i = 0, attributesSize = masterAttributes.size(); i < attributesSize; i++) {
      DBAttribute<Long> attribute = masterAttributes.get(i);
      exprs[i] = DPEquals.create(attribute, artifact);
    }
    return BoolExpr.or(exprs);
  }

  public static AttributeMap loadShadowable(DBReader reader, long artifact) {
    AttributeMap map = new AttributeMap();
    Set<DBAttribute<?>> attrs = reader.getAttributeMap(artifact).keySet();
    for (DBAttribute attr : attrs) {
      if (!SyncAttributes.isShadowable(attr, reader)) continue;
      Object value = reader.getValue(artifact, attr);
      if (value != null) map.put(attr, value);
    }
    return map;
  }

  public static DBItemType getItemType(DBReader reader, Long item) {
    if (item == null || item <= 0) return null;
    Long typeType = reader.getValue(item, DBAttribute.TYPE);
    if (typeType == null || typeType <= 0 || typeType != reader.findMaterialized(DBItemType.TYPE)) return null;
    String id = reader.getValue(item, DBAttribute.ID);
    if (id == null) return null;
    String name = reader.getValue(item, DBAttribute.NAME);
    return new DBItemType(id, name);
  }
}
