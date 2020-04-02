package com.almworks.bugzilla.provider;

import com.almworks.api.constraint.CompositeConstraint;
import com.almworks.api.constraint.FieldSubstringsConstraint;
import com.almworks.api.reduction.*;
import com.almworks.items.api.DBAttribute;

import java.util.List;

/**
 * @author dyoma
 */
class NegatedConstraintRules {
  // todo #847
  public static final Rule NEGATED_MATCHER_ALL =
    new SpecificLeafRule<FieldSubstringsConstraint>(FieldSubstringsConstraint.MATCHES_ALL) {
    protected ConstraintTreeElement process(FieldSubstringsConstraint constraint, boolean negated) {
      List<String> substrings = constraint.getSubstrings();
      if (substrings == null || substrings.isEmpty())
        return ConstraintTreeLeaf.createTrue(negated);
      if (!negated)
        return null;
      DBAttribute attibute = constraint.getAttribute();
      if (substrings.size() == 1) {
        ConstraintTreeLeaf leaf =
          new ConstraintTreeLeaf(FieldSubstringsConstraint.Simple.any(attibute, substrings.get(0)));
        leaf.negate();
        return leaf;
      }
      ConstraintTreeNode node = new ConstraintTreeNode();
      node.setType(CompositeConstraint.AND);
      for (String str : substrings) {
        ConstraintTreeLeaf leaf = new ConstraintTreeLeaf(FieldSubstringsConstraint.Simple.any(attibute, str));
        leaf.negate();
        node.addChild(leaf);
      }
      return node;
    }
  };
}
