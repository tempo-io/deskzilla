package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.*;
import com.almworks.api.constraint.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.explorer.workflow.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.dp.DPIntersects;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.images.Icons;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author dyoma
 */
public interface EnumConstraintKind {
  EnumConstraintKind INCLUSION = new InclusionEnumKind();
  EnumConstraintKind INTERSECTION = new IntersectionEnumKind();

  String INCLUSION_OPERATION = "in";
  String INTERSECTION_OPERATION = "intersects";
  String TREE_OPERATION = "under";


  BoolExpr<DP> createFilter(List<Long> items, DBAttribute attribute);

  Constraint createConstraint(List<Long> items, DBAttribute attribute);

  String getFormulaOperation();

  Icon getIcon();

  EditPrimitive<?> createReadonlyPrimitive(ModelKey<?> modelKey, ResolvedItem singleOption,
    DBAttribute attribute, boolean replaceMultiEnum, boolean clear);

  public static class InclusionEnumKind implements EnumConstraintKind {
    public BoolExpr<DP> createFilter(List<Long> items, DBAttribute attribute) {
      assert Long.class.equals(attribute.getValueClass()) : attribute;
      assert attribute.getComposition() == DBAttribute.ScalarComposition.SCALAR;
      return DPEquals.<Long>equalOneOf(attribute, items);
    }

    public Constraint createConstraint(List<Long> items, DBAttribute attribute) {
      int size = items.size();
      if (size == 0) {
        return Constraint.FALSE;
      } else if (size == 1) {
        return FieldEqualsConstraint.Simple.create(attribute, items.get(0));
      } else {
        FieldEqualsConstraint[] result = new FieldEqualsConstraint[size];
        for (int i = 0; i < size; i++) {
          Long artifact = items.get(i);
          result[i] = FieldEqualsConstraint.Simple.create(attribute, artifact);
        }
        return CompositeConstraint.Simple.or(result);
      }
    }

    public String getFormulaOperation() {
      return INCLUSION_OPERATION;
    }

    public Icon getIcon() {
      return Icons.QUERY_CONDITION_ENUM_ATTR;
    }

    public EditPrimitive<?> createReadonlyPrimitive(ModelKey<?> modelKey, ResolvedItem singleOption,
      DBAttribute attribute, boolean replaceMultiEnum, boolean clear)
    {
      return new ReadonlyEnumField((ModelKey<ItemKey>) modelKey, singleOption, attribute);
    }
  }


  public static class IntersectionEnumKind implements EnumConstraintKind {
    public BoolExpr<DP> createFilter(List<Long> items, final DBAttribute attribute) {
      assert attribute.getComposition() != DBAttribute.ScalarComposition.SCALAR : attribute;
      return DPIntersects.create(attribute, items);
    }

    public Constraint createConstraint(List<Long> items, DBAttribute attribute) {
      return FieldSubsetConstraint.Simple.intersection(attribute, items);
    }

    public String getFormulaOperation() {
      return INTERSECTION_OPERATION;
    }

    public Icon getIcon() {
      return Icons.QUERY_CONDITION_ENUM_SET;
    }

    public EditPrimitive<?> createReadonlyPrimitive(ModelKey<?> modelKey, ResolvedItem singleOption,
      DBAttribute attribute, boolean replaceMultiEnum, boolean clear)
    {
      ModelKey<List<ItemKey>> mk = (ModelKey<List<ItemKey>>) modelKey;
      List<ResolvedItem> target = Collections.singletonList(singleOption);
      return replaceMultiEnum || clear ?
        new ReadonlyEnumSetField(mk, target, attribute) :
        new ReadonlyEnumSetAddField(mk, target, attribute);
    }

    public String getReadonlyPrimitiveActionName(boolean replaceMultiEnum, boolean clear) {
      return clear ? "clear" : (replaceMultiEnum ? "replace" : "add");
    }
  }
}
