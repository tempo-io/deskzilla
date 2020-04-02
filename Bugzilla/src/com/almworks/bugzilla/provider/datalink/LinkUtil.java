package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.data.BooleanChartElementType;
import com.almworks.util.Pair;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

import static com.almworks.api.constraint.FieldEqualsConstraint.EQUALS_TO;
import static com.almworks.api.constraint.FieldIntConstraint.*;
import static com.almworks.api.constraint.FieldSubstringsConstraint.MATCHES_ALL;
import static com.almworks.api.constraint.FieldSubstringsConstraint.MATCHES_ANY;
import static com.almworks.bugzilla.integration.data.BooleanChartElementType.*;

public class LinkUtil {
  private static final Map<TypedKey<? extends Constraint>, BCType> BC_TYPES = Collections15.hashMap();

  static {
    BCType equal = new BCType(EQUALS, NOT_EQUALS);
    BC_TYPES.put(INT_EQUALS, equal);
    BC_TYPES.put(INT_GREATER, new BCType(GREATER, null));
    BC_TYPES.put(INT_LESS, new BCType(LESS, null));
    BC_TYPES.put(EQUALS_TO, equal);
    // todo #847
    BC_TYPES.put(MATCHES_ALL, new BCType(CONTAINS_ALL, null));
    BC_TYPES.put(MATCHES_ANY, new BCType(CONTAINS_ANY, NONE_OF_WORDS));

    BC_TYPES.put(FieldSubsetConstraint.INTERSECTION, new BCType(ANY_WORDS, NONE_OF_WORDS));
  }

  @Nullable
  public static BooleanChartElementType getDefaultBCElementType(OneFieldConstraint constraint, boolean negated) {
    BCType bcType = BC_TYPES.get(constraint.getType());
    BooleanChartElementType result = bcType != null ? bcType.getElementType(negated) : null;
    assert result != null : "no element type for " + constraint.getType() + " " + negated;
    return result;
  }

  /**
   * replace empty search with valid one
   */
  @Nullable
  public static Pair<String, BooleanChartElementType> fixEmptyBooleanChartCondition(String value,
    BooleanChartElementType type)
  {
    if (value != null && value.length() == 0) {
      if (type == BooleanChartElementType.EQUALS) {
        return Pair.create(".", BooleanChartElementType.DOES_NOT_CONTAIN_REGEXP);
      } else if (type == BooleanChartElementType.NOT_EQUALS) {
        return Pair.create(".", BooleanChartElementType.CONTAINS_REGEXP);
      }
    }
    return null;
  }

  private static class BCType {
    private final BooleanChartElementType myPositive;
    private final BooleanChartElementType myNegative;

    public BCType(BooleanChartElementType positive, BooleanChartElementType negative) {
      myPositive = positive;
      myNegative = negative;
    }

    public BooleanChartElementType getElementType(boolean negated) {
      return negated ? myNegative : myPositive;
    }
  }
}
