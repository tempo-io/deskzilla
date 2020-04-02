package com.almworks.bugzilla.provider;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.integration.data.BooleanChartElementType;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.util.commons.ProcedureE;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.Failure;

import java.math.BigDecimal;
import java.util.List;

public class BooleanChartMakerTests extends BugzillaConnectionFixture {
  private BooleanChartMaker myMaker;

  public BooleanChartMakerTests() {
    int i = 239;
  }

  protected void setUp() throws Exception {
    super.setUp();
    myMaker = new BooleanChartMaker();
  }

  protected void tearDown() throws Exception {
    myMaker = null;
    super.tearDown();
  }

  @CanBlock
  private void runTestInTransaction(final ProcedureE<DBDrain, Exception> test) throws InterruptedException {
    commitAndWait(new EditCommit.Adapter() {
      @Override
      public void performCommit(EditDrain drain) throws DBOperationCancelledException {
        try {
          test.invoke(drain);
        } catch (Exception e) {
          throw new Failure(e);
        }
      }
    });
  }

  public void testSingleConditions() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        BooleanChart chart;
        DBReader reader = drain.getReader();

        Constraint constraint =
          FieldSubstringsConstraint.Simple.any(metadata().getWorkspaceAttribute(BugzillaAttribute.SHORT_DESCRIPTION), "summer");
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.SHORT_DESCRIPTION, BooleanChartElementType.CONTAINS_ANY,
          "summer");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        checkSingleConditionChart(chart, BugzillaAttribute.SHORT_DESCRIPTION, BooleanChartElementType.NONE_OF_WORDS,
          "summer");

        constraint =
          FieldSubstringsConstraint.Simple.any(metadata().getWorkspaceAttribute(BugzillaAttribute.BUG_URL), "foo", "bar");
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.BUG_URL, BooleanChartElementType.CONTAINS_ANY, "foo bar");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        checkSingleConditionChart(chart, BugzillaAttribute.BUG_URL, BooleanChartElementType.NONE_OF_WORDS, "foo bar");

        chart = myMaker.createBooleanChart(FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.SEVERITY),
          createItem(BugzillaAttribute.SEVERITY, "normal", drain)), reader);
        checkSingleConditionChart(chart, BugzillaAttribute.SEVERITY, BooleanChartElementType.EQUALS, "normal");

      }
    });
  }

  public void testIntConstraints() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        BooleanChart chart;
        DBReader reader = drain.getReader();
        FieldIntConstraint constraint;

        constraint = FieldIntConstraint.Simple.equals(Bug.attrBugID, BigDecimal.valueOf(31));
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ID, BooleanChartElementType.EQUALS, "31");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ID, BooleanChartElementType.NOT_EQUALS, "31");

        constraint = FieldIntConstraint.Simple.greater(metadata().getWorkspaceAttribute(BugzillaAttribute.ESTIMATED_TIME), BigDecimal.valueOf(-9));
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ESTIMATED_TIME, BooleanChartElementType.GREATER, "-9");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        assertEquals(1, chart.getGroups().size());
        BooleanChart.Group group = chart.getGroups().get(0);
        checkElement(group.getElements().get(0), BugzillaAttribute.ESTIMATED_TIME, BooleanChartElementType.EQUALS, "-9");
        checkElement(group.getElements().get(1), BugzillaAttribute.ESTIMATED_TIME, BooleanChartElementType.LESS, "-9");

        constraint = FieldIntConstraint.Simple.less(metadata().getWorkspaceAttribute(BugzillaAttribute.ACTUAL_TIME), BigDecimal.valueOf(0));
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ACTUAL_TIME, BooleanChartElementType.LESS, "0");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        assertEquals(1, chart.getGroups().size());
        group = chart.getGroups().get(0);
        checkElement(group.getElements().get(0), BugzillaAttribute.ACTUAL_TIME, BooleanChartElementType.EQUALS, "0");
        checkElement(group.getElements().get(1), BugzillaAttribute.ACTUAL_TIME, BooleanChartElementType.GREATER, "0");
      }
    });
  }

  public void testContainsAll() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        BooleanChart chart;
        DBReader reader = drain.getReader();
        FieldSubstringsConstraint constraint;
        constraint = FieldSubstringsConstraint.Simple.all(metadata().getWorkspaceAttribute(BugzillaAttribute.ALIAS), "bell");
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ALIAS, BooleanChartElementType.CONTAINS_ALL, "bell");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        checkSingleConditionChart(chart, BugzillaAttribute.ALIAS, BooleanChartElementType.NONE_OF_WORDS, "bell");

        constraint = FieldSubstringsConstraint.Simple.all(metadata().getWorkspaceAttribute(BugzillaAttribute.STATUS_WHITEBOARD), "a", "b");
        chart = myMaker.createBooleanChart(constraint, reader);
        checkSingleConditionChart(chart, BugzillaAttribute.STATUS_WHITEBOARD, BooleanChartElementType.CONTAINS_ALL, "a b");
        chart = myMaker.createBooleanChart(not(constraint), reader);
        List<BooleanChart.Group> groups = chart.getGroups();
        assertEquals(2, groups.size());
        checkGroup(groups.get(0), BugzillaAttribute.STATUS_WHITEBOARD, BooleanChartElementType.NONE_OF_WORDS, "a");
        checkGroup(groups.get(1), BugzillaAttribute.STATUS_WHITEBOARD, BooleanChartElementType.NONE_OF_WORDS, "b");
      }
    });
  }

  private static Constraint not(Constraint constraint) {
    return new ConstraintNegation.Simple(constraint);
  }

  public void testOneLevelAnd() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        Constraint c1 = FieldIntConstraint.Simple.greater(metadata().getWorkspaceAttribute(BugzillaAttribute.ESTIMATED_TIME), BigDecimal.valueOf(-9));
        Constraint c2 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.PRIORITY),
          createItem(BugzillaAttribute.PRIORITY, "P1", drain));
        Constraint c3 = FieldSubstringsConstraint.Simple.any(metadata().getWorkspaceAttribute(BugzillaAttribute.SHORT_DESCRIPTION), "summer", "time");
        Constraint constraint = CompositeConstraint.Simple.and(new Constraint[] {c1, c2, c3});
        BooleanChart chart = myMaker.createBooleanChart(constraint, drain.getReader());

        List<BooleanChart.Group> groups = chart.getGroups();
        assertEquals(3, groups.size());

        checkGroup(groups.get(0), BugzillaAttribute.ESTIMATED_TIME, BooleanChartElementType.GREATER, "-9");
        checkGroup(groups.get(1), BugzillaAttribute.PRIORITY, BooleanChartElementType.EQUALS, "P1");
        checkGroup(groups.get(2), BugzillaAttribute.SHORT_DESCRIPTION, BooleanChartElementType.CONTAINS_ANY, "summer time");
      }
    });
  }

  public void testOneLevelOr() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        Constraint c1 = FieldIntConstraint.Simple.greater(metadata().getWorkspaceAttribute(BugzillaAttribute.ESTIMATED_TIME), BigDecimal.valueOf(-9));
        Constraint c2 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.PRIORITY),
          createItem(BugzillaAttribute.PRIORITY, "P2", drain));
        Constraint c3 = FieldSubstringsConstraint.Simple.any(metadata().getWorkspaceAttribute(BugzillaAttribute.SHORT_DESCRIPTION), "time", "summer");
        Constraint constraint = CompositeConstraint.Simple.or(new Constraint[] {c1, c2, c3});
        BooleanChart chart = myMaker.createBooleanChart(constraint, drain.getReader());

        List<BooleanChart.Group> groups = chart.getGroups();
        assertEquals(1, groups.size());
        List<BooleanChart.Element> elements = groups.get(0).getElements();
        assertEquals(3, elements.size());
        checkElement(elements.get(0), BugzillaAttribute.ESTIMATED_TIME, BooleanChartElementType.GREATER, "-9");
        checkElement(elements.get(1), BugzillaAttribute.PRIORITY, BooleanChartElementType.EQUALS, "P2");
        checkElement(elements.get(2), BugzillaAttribute.SHORT_DESCRIPTION, BooleanChartElementType.CONTAINS_ANY, "time summer");
      }
    });
  }

  public void testBasicTwoLevel() throws InterruptedException {
    runTestInTransaction(new ProcedureE<DBDrain, Exception>() {
      @Override
      public void invoke(DBDrain drain) throws Exception {
        Constraint c1 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.PRIORITY),
          createItem(BugzillaAttribute.PRIORITY, "P2", drain));
        Constraint c2 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.SEVERITY),
          createItem(BugzillaAttribute.SEVERITY, "normal", drain));

        Constraint c3 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.PRIORITY),
          createItem(BugzillaAttribute.PRIORITY, "P4", drain));
        Constraint c4 = FieldEqualsConstraint.Simple.create(
          metadata().getWorkspaceAttribute(BugzillaAttribute.SEVERITY),
          createItem(BugzillaAttribute.SEVERITY, "low", drain));

        Constraint or1 = CompositeConstraint.Simple.or(new Constraint[] {c1, c2});
        Constraint or2 = CompositeConstraint.Simple.or(new Constraint[] {c3, c4});
        Constraint constraint = CompositeConstraint.Simple.and(new Constraint[] {or1, or2});
        BooleanChart chart = myMaker.createBooleanChart(constraint, drain.getReader());

        List<BooleanChart.Group> groups = chart.getGroups();
        assertEquals(2, groups.size());
        BooleanChart.Group group1 = groups.get(0);
        List<BooleanChart.Element> elements1 = group1.getElements();
        assertEquals(2, elements1.size());
        checkElement(elements1.get(0), BugzillaAttribute.PRIORITY, BooleanChartElementType.EQUALS, "P2");
        checkElement(elements1.get(1), BugzillaAttribute.SEVERITY, BooleanChartElementType.EQUALS, "normal");
        BooleanChart.Group group2 = groups.get(1);
        List<BooleanChart.Element> elements2 = group2.getElements();
        assertEquals(2, elements2.size());
        checkElement(elements2.get(0), BugzillaAttribute.PRIORITY, BooleanChartElementType.EQUALS, "P4");
        checkElement(elements2.get(1), BugzillaAttribute.SEVERITY, BooleanChartElementType.EQUALS, "low");
      }
    });
  }


  private void checkSingleConditionChart(BooleanChart chart, BugzillaAttribute attribute, BooleanChartElementType type,
    String value) {

    List<BooleanChart.Group> groups = chart.getGroups();
    assertEquals(1, groups.size());
    checkGroup(groups.get(0), attribute, type, value);
  }

  private void checkGroup(BooleanChart.Group group, BugzillaAttribute attribute, BooleanChartElementType type,
    String value) {
    List<BooleanChart.Element> elements = group.getElements();
    assertEquals(1, elements.size());
    BooleanChart.Element element = elements.get(0);
    checkElement(element, attribute, type, value);
  }

  private static void checkElement(BooleanChart.Element element, BugzillaAttribute attribute, BooleanChartElementType type,
    String value) {
    assertEquals(attribute, element.getField());
    assertEquals(type, element.getType());
    assertEquals(value, element.getValue());
  }
}
