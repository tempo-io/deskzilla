package com.almworks.api.constraint;

import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.io.PrintStream;
import java.util.List;

/**
 * @author dyoma
 */
public class Constraints {

  @Nullable
  public static <T extends Constraint> T cast(@NotNull TypedKey<T> type, @Nullable Constraint constraint) {
    if (constraint == null)
      return null;
    return type == constraint.getType() ? type.cast(constraint) : null;
  }

  public static void printConstraint(@Nullable Constraint constraint) {
    //noinspection UseOfSystemOutOrSystemErr
    printConstraint(constraint, 0, System.out);
  }

  @SuppressWarnings({"ChainOfInstanceofChecks"})
  private static void printConstraint(@Nullable Constraint constraint, int offset, PrintStream stream) {
    boolean negated = false;
    while (true) {
      ConstraintNegation negation = cast(ConstraintNegation.NEGATION, constraint);
      if (negation != null) {
        negated = !negated;
        constraint = negation.getNegated();
      } else
        break;
    }
    for (int i = 0; i < offset; i++)
      stream.print(" ");
    if (negated)
      stream.print("! ");
    if (constraint == null) {
      stream.print("<null>");
      return;
    }
    if (constraint instanceof CompositeConstraint) {
      List<? extends Constraint> children = ((CompositeConstraint) constraint).getChildren();
      stream.println(constraint.getType().getName() + " (" + children.size() + ")");
      for (Constraint c : children)
        printConstraint(c, offset + 1, stream);
    } else if (constraint instanceof OneFieldConstraint) {
      stream.print(constraint.getType().getName());
      stream.print(" ");
      OneFieldConstraint oneFieldConstraint = (OneFieldConstraint) constraint;
      stream.print(oneFieldConstraint.getAttribute());
      stream.print(" ");
      if (constraint instanceof FieldIntConstraint)
        stream.println(((FieldIntConstraint) constraint).getIntValue());
      else if (constraint instanceof FieldSubstringsConstraint)
        stream.println(((FieldSubstringsConstraint) constraint).getSubstrings());
      else if (constraint instanceof FieldEqualsConstraint)
        stream.println(((FieldEqualsConstraint) constraint).getExpectedValue());
      else
        stream.println("<unknown>: " + constraint);
    } else if (constraint instanceof ContainsTextConstraint)
      stream.println(((ContainsTextConstraint) constraint).getWords());
    else if (constraint.getType() == Constraint.TRUE)
      stream.println("TRUE");
    else {
      stream.println("<unknown>(" + constraint.getType().getName() +"): "  + constraint);
    }
  }

  public static boolean substitute(@NotNull Substitution substitution, @NotNull Constraint constraint) throws
    IllegalSubstitutionException
  {
    ConstraintNegation not = cast(ConstraintNegation.NEGATION, constraint);
    if (not != null) {
      return !substitute(substitution, not.getNegated());
    }
    Constraint truth = cast(Constraint.TRUE, constraint);
    if (truth != null)
      return true;
    CompositeConstraint or = cast(CompositeConstraint.OR, constraint);
    if (or != null)
      return lookFor(true, substitution, or);
    CompositeConstraint and = cast(CompositeConstraint.AND, constraint);
    if (and != null)
      return !lookFor(false, substitution, and);
    return substitution.substitute(constraint);
  }

  private static boolean lookFor(boolean bool, Substitution substitution, CompositeConstraint composite) throws
    IllegalSubstitutionException
  {
    for (Constraint constraint : composite.getChildren())
      if (substitute(substitution, constraint) == bool)
        return true;
    return false;
  }

  private static boolean negate(boolean negate, boolean value) {
    return negate ? !value : value;
  }

  @Nullable
  public static Boolean checkSimpleConstant(Constraint constraint) {
    if (constraint.getType() == Constraint.TRUE)
      return true;
    ConstraintNegation not = cast(ConstraintNegation.NEGATION, constraint);
    if (not == null)
      return null;
    //noinspection SimplifiableConditionalExpression
    return not.getNegated().getType() == Constraint.TRUE ? false : null;
  }

  public interface Substitution {
    boolean substitute(@NotNull Constraint constraint) throws IllegalSubstitutionException;
  }

  public static class IllegalSubstitutionException extends Exception {
    public IllegalSubstitutionException(String message) {
      super(message);
    }

    public IllegalSubstitutionException(Constraint constraint) {
      this(String.valueOf(constraint.getType()));
    }
  }
}
