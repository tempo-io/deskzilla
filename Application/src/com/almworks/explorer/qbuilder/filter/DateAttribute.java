package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.ItemDownloadStage;
import com.almworks.api.application.qb.*;
import com.almworks.api.constraint.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPNotNull;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.parser.*;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Date;
import java.util.Map;

import static com.almworks.explorer.qbuilder.filter.EmptyQueryHelper.PK_EMPTY;
import static com.almworks.explorer.qbuilder.filter.EmptyQueryHelper.isEmptyOption;

/**
 * @author dyoma
 */
public class DateAttribute implements AttributeConstraintType<Date> {
  public static final AttributeConstraintType INSTANCE = new DateAttribute();
  static final TypedKey<DateUnit.DateValue> BEFORE = TypedKey.create("before");
  static final TypedKey<DateUnit.DateValue> AFTER = TypedKey.create("after");
  private static final TypedKey<DatePreset> PRESET = TypedKey.create("preset");
  private static final String BEFORE_OPERATION = " before ";
  private static final String AFTER_OPERATION = " after ";

  private DateAttribute() {}

  @Override
  public ConstraintEditor createEditor(ConstraintEditorNodeImpl node) {
    return new DateConstraintEditor(node);
  }

  public static PropertyMap createValues(
    @Nullable DateUnit.DateValue before, @Nullable DateUnit.DateValue after, @Nullable DatePreset preset)
  {
    final PropertyMap values = new PropertyMap();
    values.put(BEFORE, before);
    values.put(AFTER, after);
    values.put(PRESET, preset);
    return values;
  }

  @Override
  public void writeFormula(FormulaWriter writer, String conditionId, PropertyMap data) {
    if(isEmptyOption(data)) {
      writer.addToken(conditionId);
      writer.addRaw(" dateIs empty");
      return;
    }

    final DatePreset preset = data.get(PRESET);
    if(preset != null) {
      preset.write(conditionId, writer);
      return;
    }

    final DateUnit.DateValue before = data.get(BEFORE);
    final DateUnit.DateValue after = data.get(AFTER);
    if(before == null && after == null) {
      return;
    }

    if(before == null) {
      writer.addToken(conditionId);
      writer.addRaw(AFTER_OPERATION);
      writer.addToken(after.getString());
      return;
    }

    if(after != null) {
      writer = writer.createChild();
      writer.addToken(after.getString());
      writer.addRaw(BEFORE_OPERATION);
    }

    writer.addToken(conditionId);
    writer.addRaw(BEFORE_OPERATION);
    writer.addToken(before.getString());
  }

  @Override
  @Nullable
  public String suggestName(String descriptorName, PropertyMap data, Map<TypedKey<?>, ?> hints)
    throws CannotSuggestNameException
  {
    if(isEmptyOption(data)) {
      return descriptorName + " is empty";
    }

    final DatePreset preset = data.get(PRESET);
    if(preset != null) {
      return descriptorName + " " + preset.getDisplayString();
    }

    throw new CannotSuggestNameException();
  }

  @Override
  @Nullable
  public BoolExpr<DP> createFilter(DBAttribute<Date> attribute, PropertyMap data) {
    if(isEmptyOption(data)) {
      return DPNotNull.create(attribute).negate();
    }
    DateUnit.DateValue beforeThan = data.get(BEFORE);
    DateUnit.DateValue laterThan = data.get(AFTER);
    if (laterThan == null && beforeThan == null) return null;
    if (beforeThan == null) {
      BoolExpr<DP> expr = laterThan.afterExpr(attribute);
      boolean acceptNull = Util.NN(data.get(DateConstraintDescriptor.ACCEPT_NULL_IF_LATER_THAN_CONSTRAINT), false);
      return acceptNull ? expr.or(ItemDownloadStage.IS_NEW) : expr;
    }
    if (laterThan == null) return beforeThan.beforeExpr(attribute);
    return BoolExpr.and(
      laterThan.afterExpr(attribute),
      beforeThan.beforeExpr(attribute)
    );
  }

  @Override
  @Nullable
  public Constraint createConstraint(DBAttribute<Date> attribute, PropertyMap data) {
    if(isEmptyOption(data)) {
      return IsEmptyConstraint.Simple.isEmpty(attribute);
    }

    final DateConstraint before = createConstraint(attribute, data, BEFORE, DateConstraint.BEFORE);
    final DateConstraint after = createConstraint(attribute, data, AFTER, DateConstraint.AFTER);
    if(before != null && after != null) {
      return CompositeConstraint.Simple.and(before, after);
    } else if(before != null) {
      return before;
    } else if(after != null) {
      return after;
    } else {
      return null;
    }
  }

  @Nullable
  private DateConstraint createConstraint(
    DBAttribute<?> attribute, PropertyMap data,
    TypedKey<DateUnit.DateValue> valueKey, TypedKey<? extends DateConstraint> type)
  {
    DateUnit.DateValue value = data.get(valueKey);
    if (value == null)
      return null;
    Date date = value.getUptoDateValue();
    return new DateConstraint.Simple(date, type, attribute);
  }

  @Override
  public boolean isSameData(PropertyMap data1, PropertyMap data2) {
    return isEmptyOption(data1) == isEmptyOption(data2) &&
      Util.equals(data1.get(PRESET), data2.get(PRESET)) &&
      Util.equals(data1.get(BEFORE), data2.get(BEFORE)) &&
      Util.equals(data1.get(AFTER), data2.get(AFTER));
  }

  @Override
  public PropertyMap getEditorData(PropertyMap data) {
    PropertyMap values = new PropertyMap();
    DateBoundParams.BEFORE.setInitialValue(data.get(BEFORE), values);
    DateBoundParams.AFTER.setInitialValue(data.get(AFTER), values);
    DatePreset preset = data.get(PRESET);
    DateConstraintEditor.CUSTOM_MODE.setInitialValue(values, preset == null);
    DateConstraintEditor.PRESET_MODE.setInitialValue(values, preset != null);
    DatePreset.KEY.setInitialValue(values, preset);
    PK_EMPTY.setInitialValue(values, isEmptyOption(data));
    return values;
  }

  @Override
  public Icon getDescriptorIcon() {
    return Icons.QUERY_CONDITION_DATE_ATTR;
  }

  private static CommutativeParser.Greater<FilterNode> PARSER =
    new CommutativeParser.Greater<FilterNode>(BEFORE_OPERATION.trim()) {
      @Override
      protected FilterNode createTwoBound(String after, String arg, String before) throws ParseException {
        return ConstraintFilterNode.parsed(arg, INSTANCE, createValues(parseDate(before, true), parseDate(after, false), null));
      }
      @Override
      protected FilterNode createOneBound(String arg, String before) throws ParseException {
        return ConstraintFilterNode.parsed(arg, INSTANCE, createValues(parseDate(before, true), null, null));
      }
    };

  public static void register(TokenRegistry<FilterNode> registry) {
    DatePreset.RelativeInteval.register(registry);
    PARSER.register(registry);
    registry.registerInfixConstraint(AFTER_OPERATION.trim(), new InfixParser<FilterNode>() {
      @Override
      public FilterNode parse(ParserContext<FilterNode> arg, ParserContext<FilterNode> after) throws ParseException {
        return ConstraintFilterNode.parsed(arg.getSingle(), INSTANCE, createValues(null, parseDate(after.getSingle(), false), null));
      }
    });
    EmptyQueryHelper.registerEmptyParser(registry, "dateIs", INSTANCE);
  }

  private static DateUnit.DateValue parseDate(String str, boolean laterBound) throws ParseException {
    try {
      return DateUnit.DateValue.parse(str, laterBound);
    } catch (java.text.ParseException e) {
      throw new ParseException("Wrong date format: " + str);
    }
  }
}
