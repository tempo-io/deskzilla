package com.almworks.bugzilla.provider.qb.flags;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.qb.*;
import com.almworks.explorer.qbuilder.filter.CompositeFilterNode;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.parser.*;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.*;

import static com.almworks.bugzilla.provider.qb.flags.FlagConstraintDescriptor.*;
import static com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor.SUBSET;

public class FlagsParser implements FunctionParser<FilterNode>, ConstraintType {
  private static final Map<String, TypedKey<List<ItemKey>>> ENUM_KEYS;
  static {
    final Map<String, TypedKey<List<ItemKey>>> map = Collections15.hashMap();
    map.put(FLAG_TYPES_KEY.getName(), FLAG_TYPES_KEY);
    map.put(SETTERS_KEY.getName(), SETTERS_KEY);
    map.put(REQUESTEES_KEY.getName(), REQUESTEES_KEY);
    ENUM_KEYS = Collections.unmodifiableMap(map);
  }

  @Override
  public FilterNode parse(ParserContext<FilterNode> context) throws ParseException {
    final PropertyMap data = parseData(context);
    return ConstraintFilterNode.parsed(FLAGS_TOKEN, this, data);
  }

  private PropertyMap parseData(ParserContext<FilterNode> context) throws ParseException {
    if(context.isEmpty()) {
      return new PropertyMap();
    }

    final FilterNode subtree = context.parseNode();
    final List<FilterNode> leaves = extractLeavesInto(subtree, new ArrayList<FilterNode>());
    return extractDataInto(leaves, new PropertyMap());
  }

  private List<FilterNode> extractLeavesInto(FilterNode node, List<FilterNode> list) {
    if(node instanceof CompositeFilterNode) {
      final CompositeFilterNode cfn = (CompositeFilterNode)node;
      for(final FilterNode kid : cfn.getChildren()) {
        extractLeavesInto(kid, list);
      }
    } else {
      list.add(node);
    }
    return list;
  }

  private PropertyMap extractDataInto(List<FilterNode> leaves, PropertyMap data) {
    for(final FilterNode leaf : leaves) {
      extractLeafDataInto(leaf, data);
    }
    return data;
  }

  private void extractLeafDataInto(FilterNode leaf, PropertyMap data) {
    if(leaf instanceof ConstraintFilterNode) {
      extractConstraint((ConstraintFilterNode)leaf, data);
    } else if(leaf instanceof StatusNode) {
      extractStatuses((StatusNode)leaf, data);
    } else {
      assert false : leaf;
    }
  }

  private void extractConstraint(ConstraintFilterNode node, PropertyMap data) {
    final TypedKey<List<ItemKey>> key = ENUM_KEYS.get(node.getDescriptor().getId());
    if(key == null) {
      assert false;
      return;
    }
    final List<ItemKey> value = node.getValue(SUBSET);
    data.put(key, value);
  }

  private void extractStatuses(StatusNode node, PropertyMap data) {
    final int length = node.myStChars.length();
    final List<ItemKey> list = Collections15.arrayList(length);
    for(int i = 0; i < length; i++) {
      list.add(new FlagConstraintEditor.Something(
        String.valueOf(d2m(node.myStChars.charAt(i)))));
    }
    data.put(STATUSES_KEY, list);
  }

  @Override
  public ConstraintEditor createEditor(ConstraintEditorNodeImpl node) {
    return null;
  }

  @Override
  public void writeFormula(FormulaWriter writer, String conditionId, PropertyMap data) {}

  @Override
  public PropertyMap getEditorData(PropertyMap data) {
    return data;
  }

  @Override
  public String suggestName(String descriptorName, PropertyMap data, Map<TypedKey<?>, ?> hints)
  throws CannotSuggestNameException
  {
    throw new CannotSuggestNameException();
  }
}
