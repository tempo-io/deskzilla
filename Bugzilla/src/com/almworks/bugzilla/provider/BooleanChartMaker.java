package com.almworks.bugzilla.provider;

import com.almworks.api.constraint.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.reduction.*;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.provider.datalink.RemoteSearchable;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;

public class BooleanChartMaker {
  private static final List<Rule> PATCHED_CNF;
  private static final List<Rule> REMOVE_UNSUPPORTED;

  static {
    REMOVE_UNSUPPORTED = Collections15.unmodifiableArrayList(StdRules.REMOVE_GREATER_EQUAL, StdRules.REMOVE_LESS_EQUAL);
    List<Rule> rules = Collections15.arrayList();
    rules.addAll(ReductionUtil.CNF);
    rules.add(StdRules.NEGATED_GREATER);
    rules.add(StdRules.NEGATED_LESS);
    rules.add(NegatedConstraintRules.NEGATED_MATCHER_ALL);
    PATCHED_CNF = Collections.unmodifiableList(rules);
  }

  private final Rule myContainsTextRule =
    new SpecificLeafRule<ContainsTextConstraint>(ContainsTextConstraint.CONTAINS_TEXT) {
      protected ConstraintTreeElement process(ContainsTextConstraint constraint, boolean negated) {
        Constraint c1 = FieldSubstringsConstraint.Simple.all(Bug.attrSummary, constraint.getWords());
        Constraint c2 = new CommentsContainAllWords(constraint.getWords());
        ConstraintTreeNode node = new ConstraintTreeNode();
        node.setType(CompositeConstraint.OR);
        if (negated)
          node.negate();
        node.addChild(new ConstraintTreeLeaf(c1));
        node.addChild(new ConstraintTreeLeaf(c2));
        return node;
      }
    };

  @Nullable
  private final BugzillaConnection myConnection;

  public BooleanChartMaker(Connection connection) {
    myConnection = Util.castNullable(BugzillaConnection.class, connection);
  }

  public BooleanChartMaker() {
    myConnection = null;
  }

  @CanBlock
  public BooleanChart createBooleanChart(@NotNull Constraint constraint, DBReader reader) {
    ConstraintTreeElement tree = ConstraintTreeElement.createTree(constraint);
    tree = ReductionUtil.reduce(tree, myContainsTextRule);
    tree = ReductionUtil.reduce(tree, REMOVE_UNSUPPORTED);
    tree = ReductionUtil.reduce(tree, PATCHED_CNF);
    tree = Flags.processCNF(tree);
    tree = ReductionUtil.reduce(tree, PATCHED_CNF);
    constraint = tree.createConstraint();
    BooleanChart chart = new BooleanChart();
    CompositeConstraint ands = Constraints.cast(CompositeConstraint.AND, constraint);
    if (ands != null)
      for (Constraint c : ands.getChildren())
        chart.addGroup(createGroup(c, reader));
    else
      chart.addGroup(createGroup(constraint, reader));
    return chart;
  }

  @Nullable
  private BooleanChart.Group createGroup(Constraint constraint, DBReader reader) {
    BooleanChart.Group group = new BooleanChart.Group();
    CompositeConstraint ors = Constraints.cast(CompositeConstraint.OR, constraint);
    if (ors != null) {
      for (Constraint c : ors.getChildren()) {
        group.addElement(createElement(c, reader));
      }
    } else {
      group.addElement(createElement(constraint, reader));
    }
    return group.isEmpty() ? null : group;
  }

  @Nullable
  private BooleanChart.Element createElement(Constraint constraint, DBReader reader) {
    final ConstraintNegation negation = Constraints.cast(ConstraintNegation.NEGATION, constraint);
    final boolean negated;
    if(negation != null) {
      constraint = negation.getNegated();
      negated = true;
    } else {
      negated = false;
    }

    if (constraint instanceof IsEmptyConstraint) {
      return null;
    } else if (constraint instanceof OneFieldConstraint) {
      OneFieldConstraint ofc = (OneFieldConstraint) constraint;
      RemoteSearchable link = getLink(ofc, reader);
      return link != null ? link.buildBooleanChartElement(ofc, negated, reader, myConnection) : null;
    } else if (constraint instanceof CommentsContainAllWords) {
      return CommonMetadata.commentsLink.buildBooleanChartElement((CommentsContainAllWords) constraint, negated);
    } else if (Constraint.TRUE.equals(constraint.getType())) {
      if (negated)
        Log.warn("unexpected negation " + constraint);
      return null;
    } else if (BlackBoxConstraint.BLACK_BOX.equals(constraint.getType())) {
      return null;
    } else {
      Log.warn("unexpected constraint " + constraint.getType());
      return null;
    }
  }

  @Nullable
  private RemoteSearchable getLink(OneFieldConstraint constraint, DBReader reader) {
    DBAttribute attribute = constraint.getAttribute();
    return CommonMetadata.findLinkByWorkspaceAttribute(attribute, reader);
  }
}
