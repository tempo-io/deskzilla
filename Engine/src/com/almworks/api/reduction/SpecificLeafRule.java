package com.almworks.api.reduction;

import com.almworks.api.constraint.Constraint;
import com.almworks.api.constraint.Constraints;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public abstract class SpecificLeafRule<T extends Constraint> implements Rule {
  private final TypedKey<T> myType;

  protected SpecificLeafRule(TypedKey<T> type) {
    myType = type;
  }

  @Nullable
  public ConstraintTreeElement process(ConstraintTreeElement element) {
    if (!(element instanceof ConstraintTreeLeaf))
      return null;
    T constraint = Constraints.cast(myType, ((ConstraintTreeLeaf) element).getConstraint());
    if (constraint == null)
      return null;
    return process(constraint, element.isNegated());
  }

  @Nullable
  protected abstract ConstraintTreeElement process(@NotNull T constraint, boolean negated);
}
