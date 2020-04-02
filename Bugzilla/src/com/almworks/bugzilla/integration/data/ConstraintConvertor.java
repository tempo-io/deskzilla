package com.almworks.bugzilla.integration.data;

import com.almworks.api.constraint.DateConstraint;
import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.util.Pair;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.util.*;

/**
 * @author dyoma
 */
public interface ConstraintConvertor<C extends OneFieldConstraint> {
  ConstraintConvertor<DateConstraint> DELTA_TS = new DateConvertor("delta_ts", false);
  ConstraintConvertor<DateConstraint> CREATION_TS = new DateConvertor("creation_ts", false);
  ConstraintConvertor<DateConstraint> NO_CONVERTOR = new NoConvertor();

  @Nullable
  BooleanChart.Element createElement(C constraint, boolean negated);


  public class DateConvertor implements ConstraintConvertor<DateConstraint> {
    private static final DateFormat FORMAT = BugzillaDateUtil.DATE_FIELD_FORMAT;

    private final String mySpecial;
    private final boolean mySharpDates;

    public DateConvertor(String special, boolean sharpDates) {
      mySpecial = special;
      mySharpDates = sharpDates;
    }

    public BooleanChart.Element createElement(DateConstraint constraint, boolean negated) {
      Pair<String, BooleanChartElementType> pair = prepareDateBooleanChart(constraint, negated, mySharpDates);
      if (pair == null)
        return null;
      return BooleanChart.createElement(mySpecial, pair.getSecond(), pair.getFirst());
    }

    /**
     * @param sharpDates if true, extra range will be requested to get exact dates
     */
    public static Pair<String, BooleanChartElementType> prepareDateBooleanChart(DateConstraint constraint,
      boolean negated, boolean sharpDates)
    {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(constraint.getDate());
      TypedKey<? extends DateConstraint> constraintType = constraint.getType();
      boolean isgreater;
      int negativeCorrection;
      int positiveCorrection;
      if (DateConstraint.BEFORE.equals(constraintType)) {
        isgreater = false;
        negativeCorrection = sharpDates ? -1 : 0;
        positiveCorrection = 1;
      } else if (DateConstraint.AFTER.equals(constraintType)) {
        isgreater = true;
        negativeCorrection = 1;
        positiveCorrection = sharpDates ? -1 : 0;
      } else {
        assert false : constraint;
        return null;
      }
      if (negated) {
        calendar.add(Calendar.DAY_OF_MONTH, negativeCorrection);
        isgreater = !isgreater;
      } else {
        calendar.add(Calendar.DAY_OF_MONTH, positiveCorrection);
      }
      BooleanChartElementType type = isgreater ? BooleanChartElementType.GREATER : BooleanChartElementType.LESS;
      String date = FORMAT.format(calendar.getTime());
      Pair<String, BooleanChartElementType> pair = Pair.create(date, type);
      return pair;
    }

    public static Map<TypedKey<? extends OneFieldConstraint>, ConstraintConvertor<? extends OneFieldConstraint>> createConvertorMap(
      ConstraintConvertor<? extends OneFieldConstraint> convertor)
    {
      Map<TypedKey<? extends OneFieldConstraint>, ConstraintConvertor<? extends OneFieldConstraint>> map =
        Collections15.hashMap();
      map.put(DateConstraint.BEFORE, convertor);
      map.put(DateConstraint.AFTER, convertor);
      return Collections.unmodifiableMap(map);
    }

/*
    @SuppressWarnings({"RawUseOfParameterizedType"})
    public static ConstraintConvertor<? extends OneFieldConstraint> replaceEarlier(final ConstraintConvertor earlier,
      final ConstraintConvertor other)
    {
      return new ConstraintConvertor<OneFieldConstraint>() {
        public BooleanChart.Element createElement(OneFieldConstraint constraint, boolean negated) {
          TypedKey<? extends OneFieldConstraint> type = constraint.getType();
          boolean isEarlier =
            (DateConstraint.AFTER.equals(type) && !negated) || (DateConstraint.BEFORE.equals(type) && negated);
          return isEarlier ? earlier.createElement(constraint, negated) : other.createElement(constraint, negated);
        }
      };
    }
*/
  }


  public static class NoConvertor implements ConstraintConvertor<DateConstraint> {
    public BooleanChart.Element createElement(DateConstraint constraint, boolean negated) {
      return null;
    }
  }
}
