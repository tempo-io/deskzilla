package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.datalink.LinkUtil;
import com.almworks.bugzilla.provider.datalink.RemoteSearchable;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.Pair;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;

import java.util.Set;

abstract class CfRemoteSearch implements RemoteSearchable {
  public static final CfRemoteSearch TEXT_CONSTRAINT = new CfRemoteSearch() {
    @Override
    protected Pair<String, BooleanChartElementType> buildSearch(OneFieldConstraint constraint, boolean negated,
      DBReader reader)
    {
      if (!(constraint instanceof FieldSubstringsConstraint))
        return null;
      BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
      String value = TextUtil.separate(((FieldSubstringsConstraint) constraint).getSubstrings(), " ");
      return Pair.create(value, type);
    }
  };
  public static final CfRemoteSearch DATE = new CfRemoteSearch() {
    @Override
    protected Pair<String, BooleanChartElementType> buildSearch(OneFieldConstraint constraint, boolean negated,
      DBReader reader)
    {
      if (!(constraint instanceof DateConstraint)) {
        return null;
      }
      TypedKey<? extends OneFieldConstraint> type = constraint.getType();
      if (type != DateConstraint.BEFORE && type != DateConstraint.AFTER) {
        return null;
      }
      return ConstraintConvertor.DateConvertor.prepareDateBooleanChart((DateConstraint) constraint, negated, true);
    }
  };

  public static final CfRemoteSearch MULTI_ENUM = new CfRemoteSearch() {
    @Override
    protected Pair<String, BooleanChartElementType> buildSearch(OneFieldConstraint constraint, boolean negated,
      DBReader reader)
    {
      FieldSubsetConstraint subsetConstraint = FieldSubsetConstraint.INTERSECTION.cast(constraint);
      if (subsetConstraint == null)
        return buildReferenceConstraint(constraint, negated, reader);
      BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
      Set<Long> subset = subsetConstraint.getSubset();
      if (subset == null || subset.isEmpty())
        return null;
      StringBuilder r = new StringBuilder();
      for (Long ptr : subset) {
        String name = CustomField.getOptionName(SyncUtils.readTrunk(reader, ptr));
        if (name == null)
          continue;
        name = name.trim();
        if (name.isEmpty())
          continue;
        if (r.length() > 0)
          r.append(' ');
        r.append(name);
      }
      if (r.length() == 0)
        return null;
      return Pair.create(r.toString(), type);
    }
  };

  public static final CfRemoteSearch SINGLE_ENUM = new CfRemoteSearch() {
    @Override
    protected Pair<String, BooleanChartElementType> buildSearch(OneFieldConstraint constraint, boolean negated,
      DBReader reader)
    {
      return buildReferenceConstraint(constraint, negated, reader);
    }
  };

  @Override
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    String cfId = CustomField.getFieldId(reader, constraint.getAttribute());

//    long customField = reader.findMaterialized(constraint.getAttribute());
//    String cfId = reader.getValue(customField, attrFieldId);
    if (cfId == null || !cfId.startsWith("cf_")) {
      assert false : cfId + " " + constraint;
      Log.warn("invalid custom field " + cfId + " " + constraint);
      return null;
    }

//    FieldLinkType type = getType(customField, reader);
    Pair<String, BooleanChartElementType> pair = buildSearch(constraint, negated, reader);
//    CustomFieldType fieldType = getType(customField);
    if (pair == null)
      return null;
    String value = pair.getFirst();
    BooleanChartElementType chartType = pair.getSecond();
    if (value == null || chartType == null) {
      return null;
    }
    Pair<String, BooleanChartElementType> fixedPair = LinkUtil.fixEmptyBooleanChartCondition(value, chartType);
    if (fixedPair != null) {
      value = fixedPair.getFirst();
      chartType = fixedPair.getSecond();
    }
    return BooleanChart.createElement(cfId, chartType, value);
  }

  protected abstract Pair<String, BooleanChartElementType> buildSearch(OneFieldConstraint constraint, boolean negated,
    DBReader reader);

  private static Pair<String, BooleanChartElementType> buildReferenceConstraint(OneFieldConstraint constraint,
    boolean negated, DBReader reader)
  {
    FieldEqualsConstraint equalsTo = FieldEqualsConstraint.EQUALS_TO.cast(constraint);
    if (equalsTo == null)
      return null;
    BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
    Long referent = equalsTo.getExpectedValue();
    if (referent == null || referent <= 0)
      return null;
    String name = CustomField.getOptionName(SyncUtils.readTrunk(reader, referent));
    return name == null ? null : Pair.create(name, type);
  }
}
