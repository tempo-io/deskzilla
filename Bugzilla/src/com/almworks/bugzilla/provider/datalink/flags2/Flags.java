package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.constraint.*;
import com.almworks.api.reduction.*;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.integration.data.BooleanChartElementType;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.BugzillaProvider;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.CopyRemoteOperation;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.LongSet;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.Set;

public class Flags {
  public static final DataLink DATA_LINK = new Link();

  private static final DBNamespace NS = BugzillaProvider.NS.subModule("flags");

  private static final DBNamespace NS_TYPE = NS.subNs("type");
  public static final DBItemType KIND_TYPE = NS_TYPE.type();
  public static final DBAttribute<Integer> AT_TYPE_ID = NS_TYPE.integer("id", "Flag Type ID", false);
  public static final DBAttribute<String> AT_TYPE_NAME = NS_TYPE.string("name", "Flag Type Name", false);
  public static final DBAttribute<String> AT_TYPE_DESCRIPTION = NS_TYPE.string("description", "Flag Type Description", false);
  public static final DBAttribute<Integer> AT_TYPE_FLAGS = NS_TYPE.integer("flags", "Flag Type Flags", false);
  public static final DBAttribute<Set<Long>> AT_TYPE_APPLICABLE_PLUS = NS_TYPE.linkSet("appPlus", "Surely Applicable to Components", false);
  public static final DBAttribute<Set<Long>> AT_TYPE_APPLICABLE_MINUS = NS_TYPE.linkSet("appMinus", "Surely Not Applicable to Components", false);

  private static final DBNamespace NS_FLAG = NS.subNs("flag");
  public static final DBItemType KIND_FLAG = NS_FLAG.type();
  public static final DBAttribute<Long> AT_FLAG_MASTER = NS_FLAG.master("bug");
  public static final DBAttribute<Long> AT_FLAG_TYPE = NS_FLAG.link("type", "Flag Type", false);
  public static final DBAttribute<Integer> AT_FLAG_ID = NS_FLAG.integer("id", "Flag ID", false);
  public static final DBAttribute<Long> AT_FLAG_SETTER = NS_FLAG.link("setter", "Flag Setter", true);
  public static final DBAttribute<Long> AT_FLAG_REQUESTEE = NS_FLAG.link("requestee", "Flag Requestee", true);
  public static final DBAttribute<Character> AT_FLAG_STATUS = NS_FLAG.character("status", "Flag Status", true);

  static final DBNamespace NS_CACHES = NS.subNs("cache");
  public static final DBAttribute<Set<Long>> CACHE_SETTERS = NS_CACHES.linkSet("flagSetters", "flagSetters", false);
  public static final DBAttribute<Set<Long>> CACHE_REQUESTEES = NS_CACHES.linkSet("flagRequestees", "flagRequestees", false);
  public static final DBAttribute<Set<Long>> CACHE_TYPES = NS_CACHES.linkSet("flagTypes", "flagTypes", false);
  public static final ItemLink SLAVE_LINK = new FlagSlave();

  public static final DBAttribute<Boolean> AT_CONNECTION_HAS_FLAGS = NS.bool("connection.hasFlags", "Has Flags?", false);

  public static boolean isFlagAttribute(DBAttribute<?> attribute) {
    return CACHE_REQUESTEES.equals(attribute)
      || CACHE_SETTERS.equals(attribute)
      || CACHE_TYPES.equals(attribute);
  }

  /**
   * Checks if the attribute is caching and cannot be set directly outside from flags system. If yes, returns displayable name of the attribute
   * @return true
   */
  @Nullable
  public static String checkCachingAttribute(DBAttribute<?> attributeKey) {
    if (CACHE_TYPES.equals(attributeKey)) return "flag type";
    if (CACHE_SETTERS.equals(attributeKey)) return "flag setter";
    if (CACHE_REQUESTEES.equals(attributeKey)) return "flag requestee";
    return null;
  }

  public static final RemoteSearchable REMOTE_SEARCH = new MyRemoteSearch();

  public static ConstraintTreeElement processCNF(ConstraintTreeElement tree) {
    return MyRemoteSearch.processCNF(tree);
  }

  public static void registerMergers(MergeOperationsManager mm) {
    mm.addMergeOperation(new CopyRemoteOperation(AT_FLAG_SETTER), KIND_FLAG);
  }

  public static void registerTrigger(Database db) {
    db.registerTrigger(new CacheTrigger());
  }


  private static class MyRemoteSearch implements RemoteSearchable {
    private static final String BC_FLAG_TYPE = "flagtypes.name";
    private static final String BC_REQUESTEE = "requestees.login_name";
    private static final String BC_SETTER = "setters.login_name";

    @Override
    @Nullable
    public BooleanChart.Element buildBooleanChartElement(
      OneFieldConstraint c, boolean negated, DBReader reader, BugzillaConnection connection)
    {
      if (negated) {
        Log.warn("Negated constraints should be removed " + c);
        return null;
      }
      FieldSubsetConstraint constraint = Constraints.cast(FieldSubsetConstraint.INTERSECTION, c);
      if (constraint == null) return null;
      Set<Long> pointers = constraint.getSubset();
      if (pointers.isEmpty()) return null;
      DBAttribute attr = c.getAttribute();
//    FlagSingletons meta = myMeta.get();
      if (CACHE_TYPES.equals(attr)) {
        StringBuilder names = new StringBuilder();
        String sep = "";
        for (Long typePointer : pointers) {
          ItemVersion type = SyncUtils.readTrunk(reader, typePointer);
          String typeName = type.getValue(AT_TYPE_NAME);
          if (typeName == null || typeName.length() == 0) continue;
          names.append(sep);
          sep = " ";
          names.append(typeName);
        }
        return BooleanChart.createElement(BC_FLAG_TYPE, BooleanChartElementType.CONTAINS_ANY, names.toString());
      } else {
        boolean setter = CACHE_SETTERS.equals(attr);
        if (!setter && !CACHE_REQUESTEES.equals(attr)){
          Log.error("Unknown flag attribute " + attr);
          return null;
        }
        StringBuilder names = new StringBuilder();
        String sep = "";
        for (Long userPointer : pointers) {
          String userId = User.getRemoteId(SyncUtils.readTrunk(reader, userPointer));
          if (userId == null) continue;
          userId = User.stripSuffix(userId, connection);
          names.append(sep);
          sep = " ";
          names.append(userId);
        }
        return BooleanChart.createElement(setter ? BC_SETTER : BC_REQUESTEE, BooleanChartElementType.CONTAINS_ANY, names.toString());
      }
    }

    public static ConstraintTreeElement processCNF(ConstraintTreeElement cnf) {
      return new LeafProcessor() {
        DBAttribute[] attributes = {CACHE_TYPES, CACHE_SETTERS, CACHE_REQUESTEES};
        boolean[] found = new boolean[attributes.length];
        @Override
        protected ConstraintTreeElement processLeaf(ConstraintTreeLeaf leaf) {
          if (isDescendantOfOr()) return leaf;
          FieldSubsetConstraint intersection = Constraints.cast(FieldSubsetConstraint.INTERSECTION, leaf.getConstraint());
          if (intersection == null) return leaf;
          int index = ArrayUtil.indexOf(attributes, intersection.getAttribute());
          if (index < 0) return leaf;
          if (isEffectivlyNegated()) return null;
          if (intersection.getSubset().isEmpty()) return null;
          if (found[index]) return null;
          found[index] = true;
          return leaf;
        }
      }.process(cnf);
    }

  }

  private static class CacheTrigger extends DBTrigger {
    public CacheTrigger() {
      super(Flags.NS_CACHES.obj("trigger"), DPEqualsIdentified.create(DBAttribute.TYPE, KIND_FLAG));
    }

    @Override
    public void apply(LongList itemsSorted, DBWriter writer) {
      final LongSet bugs = extractBugs(itemsSorted, writer);
      for(final LongIterator it = bugs.iterator(); it.hasNext();) {
        updateCaches(it.next(), writer);
      }
    }

    private LongSet extractBugs(LongList itemsSorted, DBWriter writer) {
      final long typeFlag = writer.materialize(KIND_FLAG);

      final LongSet bugs = new LongSet();
      for(final LongIterator it = itemsSorted.iterator(); it.hasNext();) {
        final long item = it.next();
        if(item > 0) {
          final long type = Util.NN(writer.getValue(item, DBAttribute.TYPE), 0L);
          if(type == typeFlag) {
            append(writer, item, AT_FLAG_MASTER, bugs);
          }
        }
      }
      return bugs;
    }

    private void updateCaches(long bug, DBWriter writer) {
      final BoolExpr<DP> filter = BoolExpr.and(
        DPEquals.create(AT_FLAG_MASTER, bug),
        DPNotNull.create(SyncAttributes.INVISIBLE).negate());
      final LongArray flags = writer.query(filter).copyItemsSorted();

      final LongSet types = new LongSet();
      final LongSet setters = new LongSet();
      final LongSet requestees = new LongSet();
      for(final LongIterator it = flags.iterator(); it.hasNext();) {
        final long flag = it.next();
        append(writer, flag, AT_FLAG_TYPE, types);
        append(writer, flag, AT_FLAG_SETTER, setters);
        append(writer, flag, AT_FLAG_REQUESTEE, requestees);
      }

      writer.setValue(bug, CACHE_TYPES, types.toObjectSet());
      writer.setValue(bug, CACHE_SETTERS, setters.toObjectSet());
      writer.setValue(bug, CACHE_REQUESTEES, requestees.toObjectSet());
    }

    private void append(DBReader reader, long item, DBAttribute<Long> attr, LongSet set) {
      final Long value = reader.getValue(item, attr);
      if(value != null && value > 0) {
        set.add(value);
      }
    }
  }
}
