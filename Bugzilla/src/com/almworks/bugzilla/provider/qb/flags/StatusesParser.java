package com.almworks.bugzilla.provider.qb.flags;

import com.almworks.api.application.NameResolver;
import com.almworks.api.application.UnresolvedNameException;
import com.almworks.api.application.qb.*;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.items.api.DP;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.text.parser.*;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

public class StatusesParser implements FunctionParser<FilterNode> {
  @Override
  public FilterNode parse(ParserContext<FilterNode> context) throws ParseException {
    final String stChars = context.toString();
    if(FlagConstraintDescriptor.isLegalStatusString(stChars)) {
      return new StatusNode(stChars);
    } else {
      throw new ParseException(stChars + " is not a valid flag status string");
    }
  }
}

/**
 * This dummy node is only used to carry flag status selection from
 * {@link StatusesParser} to {@link FlagsParser} via the node tree.
 */
class StatusNode implements FilterNode {
  final String myStChars;

  public StatusNode(String stChars) {
    myStChars = stChars;
  }

  @Override
  public Constraint createConstraint(ItemHypercube cube) {
    assert false;
    return null;
  }

  @NotNull
  @Override
  public EditorNode createEditorNode(@NotNull EditorContext context) {
    assert false;
    return null;
  }

  @Override
  public BoolExpr<DP> createFilter(ItemHypercube hypercube) throws UnresolvedNameException {
    assert false;
    return null;
  }

  @Override
  public boolean isSame(@Nullable FilterNode filterNode) {
    assert false;
    return false;
  }

  @Override
  public void normalizeNames(@NotNull NameResolver resolver, ItemHypercube cube) {
    assert false;
  }

  @Override
  public FilterNode createCopy() {
    assert false;
    return this;
  }

  @Override
  public ConstraintType getType() {
    assert false;
    return null;
  }

  @Override
  public String getSuggestedName(Map<TypedKey<?>, ?> hints) throws CannotSuggestNameException {
    assert false;
    return null;
  }

  @Override
  public void writeFormula(FormulaWriter writer) {
    assert false;
  }
}

