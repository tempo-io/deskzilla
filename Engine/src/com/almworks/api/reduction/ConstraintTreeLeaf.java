package com.almworks.api.reduction;

import com.almworks.api.constraint.Constraint;
import org.jetbrains.annotations.*;

/**
 * @author Vasya
 */
public class ConstraintTreeLeaf extends ConstraintTreeElement {
  @NotNull
  private final Constraint myConstraint;

  public ConstraintTreeLeaf(@NotNull Constraint constraint) {
    myConstraint = constraint;
  }

  public ConstraintTreeLeaf getCopy() {
    ConstraintTreeLeaf result = new ConstraintTreeLeaf(myConstraint);
    if (isNegated())
      result.negate();
    return result;
  }

  @NotNull
  protected Constraint createConstraintImpl() {
    return myConstraint;
  }

  @NotNull
  public Constraint getConstraint() {
    return myConstraint;
  }

  public String toString() {
    return (isNegated() ? "!" : "") + myConstraint.toString();
  }

  public static ConstraintTreeLeaf createTrue() {
    return new ConstraintTreeLeaf(Constraint.NO_CONSTRAINT);
  }

  public static ConstraintTreeLeaf createFalse() {
    return createTrue(true);
  }

  public static ConstraintTreeLeaf createTrue(boolean negated) {
    ConstraintTreeLeaf aTrue = createTrue();
    if (negated)
      aTrue.negate();
    return aTrue;
  }
}
