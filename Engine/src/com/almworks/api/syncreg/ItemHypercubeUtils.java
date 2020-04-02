package com.almworks.api.syncreg;

import com.almworks.api.constraint.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.reduction.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.collections.Containers;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class ItemHypercubeUtils {
  private static final ReplaceIntersectionWithEqualsRule INTERSECTION_TO_EQUALS = new ReplaceIntersectionWithEqualsRule();

  private ItemHypercubeUtils() {
  }

  /**
   * @param constraint
   * @param precise
   * @return hypercube if can build. Null means "FALSE" hypercube (no item satisfies the cube) or cannot build precise cube
   * when precise cube is requested.
   */
  public static ItemHypercubeImpl getHypercube(@NotNull Constraint constraint, boolean precise) {
    try {
      constraint = reduceForHypercube(constraint);
      CompositeConstraint and = Constraints.cast(CompositeConstraint.AND, constraint);
      ItemHypercubeImpl result;
      if (and != null) {
        result = null;
        for (Constraint child : and.getChildren()) {
          if (result == null) {
            result = createForSingleAxis(precise, child);
          } else {
            ItemHypercubeImpl cube = createForSingleAxis(precise, child);
            if (cube == null) return null;
            result = result.intersect(cube, precise);
            if (result == null) return null;
          }
        }
        if (result == null) result = new ItemHypercubeImpl();
      } else {
        result = createForSingleAxis(precise, constraint);
      }
      return result;
    } catch (CannotBuildPreciseHypercubeException e) {
      assert precise : e;
      return null;
    }
  }

  /**
   * @param precise
   * @param child
   * @return cube if can build. null means "FALSE" cube (no item satisfies)
   * @throws CannotBuildPreciseHypercubeException precise cube is requested but cannot be built
   */
  private static ItemHypercubeImpl createForSingleAxis(boolean precise, Constraint child)
    throws CannotBuildPreciseHypercubeException
  {
    ItemHypercubeImpl result = new ItemHypercubeImpl();
    TypedKey type = child.getType();
    boolean sign = true;
    if (type == ConstraintNegation.NEGATION) {
      ConstraintNegation negation = ConstraintNegation.NEGATION.cast(child);
      assert negation != null;
      child = negation.getNegated();
      type = child.getType();
      sign = false;
    }
    if (type == FieldEqualsConstraint.EQUALS_TO) {
      FieldEqualsConstraint equalsTo = FieldEqualsConstraint.EQUALS_TO.cast(child);
      assert equalsTo != null;
      result.addValue(equalsTo.getAttribute(), equalsTo.getExpectedValue(), sign);
    } else if (type == CompositeConstraint.OR) {
      // or should not come under not
      assert sign : child;
      CompositeConstraint or = CompositeConstraint.OR.cast(child);
      assert or != null;
      addSingleAxisFromOr(result, or.getChildren(), precise);
    } else if (type == Constraint.TRUE) {
      if (!sign) return precise ? null : result;
      // do nothing
    } else {
      if (precise)
        throw new CannotBuildPreciseHypercubeException();
    }
    return result;
  }

  private static Constraint reduceForHypercube(@NotNull Constraint constraint) {
    ConstraintTreeElement r0 = ConstraintTreeElement.createTree(constraint);
    ConstraintTreeElement r1 = ReductionUtil.reduce(r0, INTERSECTION_TO_EQUALS);
    ConstraintTreeElement r2 = ReductionUtil.toCnf(r1);
    return r2.createConstraint();
  }

  private static void addSingleAxisFromOr(ItemHypercubeImpl cube, List<? extends Constraint> children, boolean precise)
    throws CannotBuildPreciseHypercubeException
  {
    DBAttribute singleAttribute = null;
    SortedSet<Long> included = null;
    SortedSet<Long> excluded = null;
    for (Constraint c : children) {
      boolean sign = true;
      TypedKey<? extends Constraint> type = c.getType();
      if (type == ConstraintNegation.NEGATION) {
        ConstraintNegation negation = ConstraintNegation.NEGATION.cast(c);
        assert negation != null;
        c = negation.getNegated();
        type = c.getType();
        sign = false;
      }
      if (type != FieldEqualsConstraint.EQUALS_TO) {
        // cannot add dimension disjunctive with some other constraint
        if (precise)
          throw new CannotBuildPreciseHypercubeException();
        return;
      }
      FieldEqualsConstraint equals = FieldEqualsConstraint.EQUALS_TO.cast(c);
      assert equals != null;
      DBAttribute attribute = equals.getAttribute();
      if (singleAttribute != null) {
        if (!Util.equals(attribute, singleAttribute)) {
          // cannot add dimension disjunctive with some other dimension, see #961
          if (precise)
            throw new CannotBuildPreciseHypercubeException();
          return;
        }
      } else {
        singleAttribute = attribute;
      }
      if (sign) {
        if (included == null)
          included = Collections15.treeSet();
        included.add(equals.getExpectedValue());
      } else {
        if (excluded == null)
          excluded = Collections15.treeSet();
        excluded.add(equals.getExpectedValue());
      }
    }

    if (singleAttribute != null) {
      if (excluded != null && included != null) {
        // strange situation, but nevertheless
        Set<Long> removed = Collections15.hashSet(excluded);
        removed.retainAll(included);
        excluded.removeAll(removed);
        included.removeAll(removed);
      }
      if (excluded != null && excluded.size() > 0) {
        cube.addAxisExcluded(singleAttribute, excluded);
      } else if (included != null && included.size() > 0) {
        cube.addAxisIncluded(singleAttribute, included);
      }
    }
  }

  public static ItemHypercube adjustForConnection(ItemHypercube hypercube, Connection connection) {
    if (connection == null || !(hypercube instanceof ItemHypercubeImpl))
      return hypercube;
    long connectionItem = connection.getConnectionItem();
    return adjustForConnection(hypercube, connectionItem);
  }

  public static ItemHypercube adjustForConnection(ItemHypercube hypercube, long connectionItem) {
    final SortedSet<Long> excluded = hypercube.getExcludedValues(SyncAttributes.CONNECTION);
    if (excluded == null || !excluded.contains(connectionItem)) {
      ((ItemHypercubeImpl) hypercube).addValue(SyncAttributes.CONNECTION, connectionItem, true);
    }
    return hypercube;
  }

  public static ItemHypercube ensureValuesIncludedForAxis(
    @Nullable ItemHypercube hypercube, @NotNull DBAttribute<?> axis, @NotNull Collection<Long> values)
  {
    if (! (hypercube instanceof ItemHypercubeImpl)) {
      assert false : hypercube;
      Log.warn("AH.ensureValuesIncludedForAxis: unknown implementation " + hypercube);
    } else {
      Set<Long> incl = hypercube.getIncludedValues(axis);
      Set<Long> excl = hypercube.getExcludedValues(axis);
      Collection<Long> toAdd = Containers.complement(values, incl);
      if(!toAdd.isEmpty()) {
        ((ItemHypercubeImpl) hypercube).addValues(axis, values, true);
      }
      assert !Containers.intersects(excl, values);
    }
    return hypercube;
  }

  public static ItemHypercubeImpl createConnectionCube(@NotNull Connection connection) {
    ItemHypercubeImpl cube = new ItemHypercubeImpl();
    adjustForConnection(cube, connection);
    return cube;
  }

  public static ItemHypercubeImpl createConnectionCube(long connectionItem) {
    ItemHypercubeImpl cube = new ItemHypercubeImpl();
    adjustForConnection(cube, connectionItem);
    return cube;
  }

  public static Collection<Long> getIncludedConnections(ItemHypercube hypercube) {
    return Util.NN(hypercube.getIncludedValues(SyncAttributes.CONNECTION), Collections.EMPTY_LIST);
  }

  private static class CannotBuildPreciseHypercubeException extends Exception {
  }


  public static class ReplaceIntersectionWithEqualsRule extends SpecificLeafRule<FieldSubsetConstraint> {
    private final Collection<DBAttribute> myAttributes;

    public ReplaceIntersectionWithEqualsRule(Collection<DBAttribute> attributes) {
      super(FieldSubsetConstraint.INTERSECTION);
      myAttributes = attributes != null ? new ArrayList<DBAttribute>(attributes) : null;
    }

    private ReplaceIntersectionWithEqualsRule() {
      this(null);
    }

    protected ConstraintTreeElement process(FieldSubsetConstraint constraint, boolean negated) {
      if (myAttributes != null && !myAttributes.contains(constraint.getAttribute())) return null;
      Set<Long> subset = constraint.getSubset();
      if (subset.size() == 0) {
        return ConstraintTreeElement.createTree(Constraint.NO_CONSTRAINT);
      }
      DBAttribute attribute = constraint.getAttribute();

      List<Constraint> options = Collections15.arrayList();
      for (Long value : subset) {
        options.add(FieldEqualsConstraint.Simple.create(attribute, value));
      }
      Constraint c = CompositeConstraint.Simple.or(options);
      if (negated) {
        c = new ConstraintNegation.Simple(c);
      }

      return ConstraintTreeElement.createTree(c);
    }
  }
}
