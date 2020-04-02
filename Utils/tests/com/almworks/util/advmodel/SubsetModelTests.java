package com.almworks.util.advmodel;

import com.almworks.util.tests.CollectionsCompare;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.detach.Lifespan;

import java.util.Collections;

/**
 * @author : Dyoma
 */
public class SubsetModelTests extends GUITestCase {
  private final CollectionsCompare CHECK = new CollectionsCompare();
  private final OrderListModel<String> myModel = OrderListModel.create();

  public void testAddingToSubset() {
    SubsetModel<String> subset = SubsetModel.create(Lifespan.FOREVER, myModel, true);
    myModel.addElement("1");
    CHECK.singleElement("1", subset.toList());
    CHECK.empty(subset.getComplementSet().toList());
  }

  public void testAddingToComplement() {
    SubsetModel<String> subset = SubsetModel.create(Lifespan.FOREVER, myModel, false);
    AListModel<String> complementSet = subset.getComplementSet();
    myModel.addElement("1");
    CHECK.singleElement("1", complementSet.toList());
    CHECK.empty(subset.toList());
  }

  public void testComplementSetLeftOverCausedBySortedListDecoratorBug() {
    myModel.addAll(new String[] {"5", "3", "4", "1", "2"});
    SubsetModel<String> model = SubsetModel.create(Lifespan.FOREVER, myModel, false);
    model.addFromFullSet(new int[] {1, 2, 3});
    AListModel<String> unselected =
      SortedListDecorator.createForComparables(Lifespan.FOREVER, model.getComplementSet());
    AListModel<String> selected = SortedListDecorator.createForComparables(Lifespan.FOREVER, model);
    CHECK.order(new String[] {"1", "3", "4"}, selected.toList());
    CHECK.order(new String[] {"2", "5"}, unselected.toList());
    model.removeAllAt(new int[] {0, 1, 2});
    CHECK.order(new String[] {}, selected.toList());
    CHECK.order(new String[] {"1", "2", "3", "4", "5"}, unselected.toList());
    model.addFromComplementSet(Collections.singletonList("2"));
    CHECK.singleElement("2", model.toList());
    CHECK.unordered(model.getComplementSet().toList(), new String[]{"1",  "3", "4", "5"});
    CHECK.order(new String[] {"2"}, selected.toList());
    CHECK.order(new String[] {"1",  "3", "4", "5"}, unselected.toList());
  }
}
