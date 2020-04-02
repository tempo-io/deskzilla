package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.ItemKeyCache;
import com.almworks.api.application.NameResolver;
import com.almworks.api.application.qb.*;
import com.almworks.api.constraint.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.TODO;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.text.parser.*;
import com.almworks.util.threads.ThreadAWT;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.util.*;

/**
 * @author : Dyoma
 */
public class FilterParserTests extends BaseTestCase {
  private final TokenRegistry<FilterNode> myRegistry = new TokenRegistry<FilterNode>();
  public static final DBAttribute<Date> DATE = DBAttribute.Date("a", "a");

  protected void setUp() throws Exception {
    super.setUp();
    FilterGramma.registerParsers(myRegistry);
  }

  public void testOperationPriority() throws ParseException, IOException {
    CompositeFilterNode or = (CompositeFilterNode) parseText("a containsAll 1&b containsAll 2|c containsAll 3");
    assertEquals(BinaryCommutative.Or.class, or.getClass());
    List<FilterNode> children = or.getChildren();
    assertEquals(2, children.size());
    BinaryCommutative.And and = (BinaryCommutative.And) children.get(0);
    assertEquals(2, and.getChildren().size());
    checkChildAttrEquals(and, 0, "a", "1");


    assertEquals("((((a containsAll 1)&(b containsAll 2)))|(c containsAll 3))", FormulaWriter.write(or));
  }

  public void testCompositeWithBraces() throws ParseException {
    CompositeFilterNode or = (CompositeFilterNode) parseText("((a containsAll (1))&b containsAll (2))|c containsAll 3");
    assertEquals(2, or.getChildren().size());
    BinaryCommutative.And and = (BinaryCommutative.And) or.getChildren().get(0);
    assertEquals(2, and.getChildren().size());
  }

  public void testNot() throws ParseException {
    CompositeFilterNode not = (CompositeFilterNode) parseText("not (a containsAll 1)");
    assertEquals(1, not.getChildren().size());
    checkChildAttrEquals(not, 0, "a", "1");
    assertEquals("not ((a containsAll 1))", FormulaWriter.write(not));

    not = (CompositeFilterNode) parseText("not (a containsAll 1&b containsAll 2)");
    assertEquals(1, not.getChildren().size());
    BinaryCommutative.And and = (BinaryCommutative.And) not.getChildren().get(0);
    checkChildAttrEquals(and, 0, "a", "1");
    checkChildAttrEquals(and, 1, "b", "2");
    assertEquals("not ((((a containsAll 1)&(b containsAll 2))))", FormulaWriter.write(not));
  }

  public void testCompositeNot() throws ParseException {
    CompositeFilterNode neither = (CompositeFilterNode) parseText("not (a containsAll 1|b containsAll 2)");
    assertEquals(2, neither.getChildren().size());
    checkChildAttrEquals(neither, 0, "a", "1");
    checkChildAttrEquals(neither, 1, "b", "2");
    assertEquals("not (((a containsAll 1)|(b containsAll 2)))", FormulaWriter.write(neither));
  }

  @SuppressWarnings({"deprecation"})
  public void testParseDate() throws ParseException {
    FilterNode node = parseText("\"1/2/05\" before a before \"4/5/05\"");
    MockNameResolver resolver = new MockNameResolver();
    resolver.addConditionDescriptor(AttributeConstraintDescriptor.constant(DateAttribute.INSTANCE, "disp", DATE));
    node.normalizeNames(resolver, new ItemHypercubeImpl());
    Constraint twoBounds = node.createConstraint(new ItemHypercubeImpl());
    CompositeConstraint and = Constraints.cast(CompositeConstraint.AND, twoBounds);
    assertNotNull(and);
    List<? extends Constraint> children = and.getChildren();
    assertEquals(2, children.size());
    DateConstraint before = Constraints.cast(DateConstraint.BEFORE, children.get(0));
    DateConstraint after = Constraints.cast(DateConstraint.AFTER, children.get(1));
    assertNotNull(before);
    assertNotNull(after);
    Date date = new Date();
    date.setTime(0);
    date.setHours(0);
    date.setYear(105);

    date.setMonth(3);
    date.setDate(5);
    assertEquals(date, before.getDate());
    date.setDate(2);
    date.setMonth(0);
    assertEquals(date, after.getDate());
    assertEquals("(\"1/2/05\" before a before \"4/5/05\")", FormulaWriter.write(node));

    node = parseText("a before \"1/2/05\"");
    node.normalizeNames(resolver, new ItemHypercubeImpl());
    before = Constraints.cast(DateConstraint.BEFORE, node.createConstraint(new ItemHypercubeImpl()));
    assertNotNull(before);
    assertEquals(date, before.getDate());
    assertEquals("a before \"1/2/05\"", FormulaWriter.write(node));

    node = parseText("a after \"1/2/05\"");
    node.normalizeNames(resolver, new ItemHypercubeImpl());
    after = Constraints.cast(DateConstraint.AFTER, node.createConstraint(new ItemHypercubeImpl()));
    assertNotNull(after);
    assertEquals(date, after.getDate());
    assertEquals("a after \"1/2/05\"", FormulaWriter.write(node));
  }

  private FilterNode parseText(String text) throws ParseException {
    ParserContext<FilterNode> context = myRegistry.tokenize(text);
    return context.parseNode();
  }

  private static void checkChildAttrEquals(CompositeFilterNode parent, int childIndex, String attributeId, String value) {
    checkAttrEquals((ConstraintFilterNode) parent.getChildren().get(childIndex), attributeId, value);
  }

  private static void checkAttrEquals(ConstraintFilterNode equalsNode, String attributeId, String value) {
    assertEquals(attributeId, equalsNode.getDescriptor().getId());
    assertEquals(value, equalsNode.getValue(TextAttribute.TEXT));
  }

  private static class MockNameResolver implements NameResolver {
    private final HashMap<String,ConstraintDescriptor> myDescriptors = Collections15.hashMap();

    @ThreadAWT
    @NotNull
    public AListModel<ConstraintDescriptor> getConstraintDescriptorModel(Lifespan life, ItemHypercube cube) {
      throw TODO.notImplementedYet();
    }

    @NotNull
    @Override
    public AListModel<ConstraintDescriptor> getAllDescriptorsModel() {
      throw TODO.notImplementedYet();
    }

    @Nullable
    public ConstraintDescriptor getConditionDescriptor(String id, @NotNull ItemHypercube cube) {
      return myDescriptors.get(id);
    }

    @NotNull
    public Modifiable getModifiable() {
      throw TODO.notImplementedYet();
    }

    public ItemKeyCache getCache() {
      throw TODO.notImplementedYet();
    }

    public void addConditionDescriptor(ConstraintDescriptor descriptor) {
      myDescriptors.put(descriptor.getId(), descriptor);
    }
  }
}