package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;

import java.util.Collection;
import java.util.Iterator;

/**
 * :todoc:
 *
 * @author sereda
 */
public class IObjectTests extends WorkspaceFixture {
  public void testAccess() throws InterruptedException {
//    ArtifactView rootView = myWorkspace.getViews().getRootView();
//    Filter filter = myWorkspace.getFilterManager().attributeEquals(SystemObjects.ATTRIBUTE.TYPE,
//      SystemObjects.TYPE.ATTRIBUTE, true);
//    ArtifactView filteredView = rootView.filter(filter);
//    Collection<? extends Revision> objects = filteredView.getAllArtifacts();
//    final int count = objects.size();
//    assertTrue(count > 0);
//    final int[] counter = {0};
//    for (Iterator<? extends Revision> it = objects.iterator(); it.hasNext();) {
//      Artifact object = it.next().getArtifact();
//      ScalarModel<Revision> lastRevisionModel = object.getChain(RevisionAccess.ACCESS_DEFAULT).getLastRevisionModel();
//      lastRevisionModel.getEventSource().addStraightListener(new ScalarModel.Adapter<Revision>() {
//        public void onScalarChanged(ScalarModelEvent<Revision> event) {
//          Revision revision = event.getNewValue();
//          String name = revision.getValue(SystemObjects.ATTRIBUTE.NAME);
//          //System.out.println(name); //todo
//          counter[0]++;
//        }
//      });
//    }
//    assertEquals(count, counter[0]);
  }

  public void testChange() throws InterruptedException {
    ArtifactView rootView = myWorkspace.getViews().getRootView();
    Attribute nameAttribute = myWorkspace.getSystemObject(SystemObjects.ATTRIBUTE.NAME);
    Filter filter = myWorkspace.getFilterManager().attributeEquals(SystemObjects.ATTRIBUTE.TYPE,
      SystemObjects.TYPE.ATTRIBUTE, true);
    ArtifactView filteredView = rootView.filter(filter);
    Collection<? extends Revision> objects = filteredView.getAllArtifacts();
    final int count = objects.size();
    assertTrue(count > 0);
    final int[] counter = {0};
    for (Iterator<? extends Revision> it = objects.iterator(); it.hasNext();) {
      Artifact object = it.next().getArtifact();
      Transaction transaction = myWorkspace.beginTransaction();
      RevisionCreator creator = transaction.changeArtifact(object, RevisionAccess.ACCESS_DEFAULT);
      String name = creator.asRevision().getValue(SystemObjects.ATTRIBUTE.NAME);
      name = name + "X";
      creator.setValue(nameAttribute, name);
      transaction.commitUnsafe();
      counter[0]++;
    }
    assertTrue(counter[0] > 0);
    testAccess();
  }

  public void testDisplayableName() {
    String s1 = myAttributeOne.getName();
    String s2 = myAttributeOne.getDisplayableName();
    assertTrue(s1.equals(s2));
    setObjectValue(myAttributeOne.getArtifact(), myWorkspace.getSystemObject(SystemObjects.ATTRIBUTE.DISPLAYABLE_NAME),
      "aaa");
    assertTrue(myAttributeOne.getName().equals(s1));
    assertTrue(myAttributeOne.getDisplayableName().equals("aaa"));
  }
}
