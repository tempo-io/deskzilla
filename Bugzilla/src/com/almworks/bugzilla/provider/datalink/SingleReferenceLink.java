package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.application.ResolvedItem;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.*;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.util.commons.LongObjFunction2;
import org.jetbrains.annotations.*;

public abstract class SingleReferenceLink<K> extends ReferenceLink<Long, K> {
  protected SingleReferenceLink(
    DBAttribute<Long> attribute, BugzillaAttribute bugzillaAttribute,
    DBItemType referentType, DBAttribute<K> referentUniqueKey, DBAttribute<K> referentVisualKey,
    boolean ignoreEmpty, K defaultValue, boolean inPrototype)
  {
    super(attribute, bugzillaAttribute,
      referentType, referentUniqueKey, referentVisualKey,
      ignoreEmpty, defaultValue, inPrototype);
  }

  public void updateRevision(
    final PrivateMetadata privateMetadata, ItemVersionCreator bugCreator,
    final BugInfo bugInfo, @NotNull BugzillaContext context)
  {
    if (cannotTell(bugInfo)) return;

    final String value = getRemoteString(bugInfo, privateMetadata);
    if (value != null) {
      long referent = getOrCreateReferent(privateMetadata, fromString(value), bugCreator);
      bugCreator.setValue(getAttribute(), referent);
    } else {
      bugCreator.setValue(getAttribute(), (Long)null);
    }
  }

  public void autoMerge(AutoMergeData data) {
    //done
    assert BugzillaUtil.assertScalarValueType(getAttribute());
    resolveToEqualValue(data);
  }

  @Override
  protected Long getPrototypeValue(PrivateMetadata pm, final DBReader reader) {
    // find minimal referent
     class OrdRef {
      Object ord;
      long ref;
      OrdRef() { this (null, 0L);}
      OrdRef(Object o, long r) { ord = o; ref = r; }
    }
    long minRef = getReferentsView(pm).query(reader).fold(new OrdRef(), new LongObjFunction2<OrdRef>() {
      @Override
      public OrdRef invoke(long referent, OrdRef minOrdRef) {
        Integer order = reader.getValue(referent, ResolvedItem.attrOrder);
        Object minOrd = minOrdRef.ord;
        if (order != null) {
          if (minOrd == null || !(minOrd instanceof Integer) || (Integer) minOrd > order)
            return new OrdRef(order, referent);
        } else {
          K key = reader.getValue(referent, getReferentUniqueKey());
          if (key != null && (minOrd == null || ((minOrd instanceof String) &&
            String.CASE_INSENSITIVE_ORDER.compare((String) minOrd, SingleReferenceLink.this.toString(key)) > 0)))
          {
            return new OrdRef(key, referent);
          }
        }
        return minOrdRef;
      }
    }).ref;
    return minRef <= 0L ? null : minRef;
  }

  @Override
  public String createRemoteString(Long item, ItemVersion lastServerVersion) {
    if(item == null || item <= 0) {
      return null;
    }
    final ItemVersion referent = lastServerVersion.forItem(item);

    final String visual = toString(referent.getValue(getReferentVisualKey()));
    if(visual != null) {
      return visual;
    }
    return toString(referent.getValue(getReferentUniqueKey()));
  }
}
