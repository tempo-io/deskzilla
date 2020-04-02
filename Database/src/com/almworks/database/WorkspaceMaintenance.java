package com.almworks.database;

import com.almworks.api.install.Setup;
import com.almworks.api.universe.*;
import com.almworks.api.universe.index.*;
import com.almworks.database.schema.Schema;
import com.almworks.util.commons.Condition;

import java.util.Comparator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class WorkspaceMaintenance {
  public static void repair(Universe universe) {
    createMissingUniverseIndexes(universe);
  }

  private static void createMissingUniverseIndexes(Universe universe) {
    ensureIndex(universe, Schema.INDEX_SYSTEM_SINGLETONS,
      FieldComparator.createLong(Schema.KS_SINGLETON_NAME),
      Condition.and(
        FieldEqualsCondition.create(Schema.KL_ATOM_MARKER.getKey(), Schema.ATOM_SYSTEM_SINGLETON_TOKEN.value()),
        FieldCondition.create(Schema.KS_SINGLETON_NAME)));

    ensureIndex(universe, Schema.INDEX_KA_PREV_ATOM,
      FieldComparator.createLong(Schema.KA_PREV_ATOM),
      FieldCondition.create(Schema.KA_PREV_ATOM));

    ensureIndex(universe, Schema.INDEX_KA_CHAIN_HEAD,
      FieldComparator.createLong(Schema.KA_CHAIN_HEAD),
      FieldCondition.create(Schema.KA_CHAIN_HEAD));

    ensureIndex(universe, Schema.INDEX_RCB_ENDLINK_LC,
      FieldComparator.createLong(Schema.KA_RCB_LINK_LOCALCHAIN),
      Condition.and(
        FieldCondition.create(Schema.KA_RCB_LINK_LOCALCHAIN),
        FieldCondition.create(Schema.KA_RCB_LINK_ENDLOCALBRANCH)));

    ensureIndex(universe, Schema.INDEX_RCB_ENDLINK_MC,
      FieldComparator.createLong(Schema.KA_RCB_LINK_ENDLOCALBRANCH),
      Condition.and(
        FieldCondition.create(Schema.KA_RCB_LINK_LOCALCHAIN),
        FieldCondition.create(Schema.KA_RCB_LINK_ENDLOCALBRANCH)));

    ensureIndex(universe, Schema.INDEX_RCB_STARTLINK_MC,
      FieldComparator.createLong(Schema.KA_RCB_LINK_BEGINLOCALBRANCH),
      Condition.and(
        FieldCondition.create(Schema.KA_RCB_LINK_LOCALCHAIN),
        FieldCondition.create(Schema.KA_RCB_LINK_BEGINLOCALBRANCH)));

    ensureIndex(universe, Schema.INDEX_RCB_STARTLINK_LC,
      FieldComparator.createLong(Schema.KA_RCB_LINK_LOCALCHAIN),
      Condition.and(
        FieldCondition.create(Schema.KA_RCB_LINK_LOCALCHAIN),
        FieldCondition.create(Schema.KA_RCB_LINK_BEGINLOCALBRANCH)));


    boolean compat = Setup.compatibilityRequired(Setup.COMPATIBLE_0_3);
    Long[] markers = new Long[] {Schema.ATOM_LOCAL_ARTIFACT.value(), Schema.ATOM_RCB_ARTIFACT.value()};
    Condition<Atom> condition = FieldOneOfCondition.create(Schema.KL_ATOM_MARKER.getKey(), markers);
    if (compat) {
      condition = Condition.or(
        condition,
        Condition.and(
          FieldEqualsCondition.create(Schema.KL_ATOM_MARKER.getKey(), Schema.ATOM_CHAIN_HEAD.value()),
          Condition.not(FieldCondition.create(Schema.KA_CHAIN_ARTIFACT))
        ));
    }
    ensureIndex(universe, Schema.INDEX_ARTIFACTS, new UCNComparator(), condition);

    ensureIndex(universe, Schema.INDEX_RCB_MERGE, FieldComparator.createLong(Schema.KA_MERGE_CHAIN_HEAD),
      FieldCondition.create(Schema.KA_MERGE_CHAIN_HEAD));
  }

  private static void ensureIndex(Universe universe, String indexName, Comparator comparator, Condition<Atom> condition) {
    Index index = universe.getIndex(indexName);
    if (index == null) {
      universe.createIndex(new IndexInfo(indexName, comparator, condition));
      assert universe.getIndex(indexName) != null;
    }
  }
}
