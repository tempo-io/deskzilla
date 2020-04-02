package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DatabaseUtilTests extends WorkspaceFixture {
  public void testMetadataCollection() {
    SimpleCollection collection = new SimpleCollection();
    collection.initialize(myWorkspace);
    assertEquals("1", collection.S1.getArtifact().getLastRevision().getValue(SystemObjects.ATTRIBUTE.ID));
    assertEquals("2", collection.S2.getArtifact().getLastRevision().getValue(SystemObjects.ATTRIBUTE.ID));
    assertEquals("a1", collection.S1.getArtifact().getLastRevision().getValue(myAttributeOne).getValue(String.class));
    assertEquals("a2", collection.S2.getArtifact().getLastRevision().getValue(myAttributeOne).getValue(String.class));
  }

  public void testDoubles() {
    DoubleCollection collection = new DoubleCollection();
    collection.initialize(myWorkspace);
    assertEquals(collection.S1, collection.S2);
    assertEquals(collection.S1.getArtifact(), collection.S2.getArtifact());
  }

  public void testDoubleDoubles() {
    DoubleCollection collection1 = new DoubleCollection();
    DoubleCollection collection2 = new DoubleCollection();
    Transaction transaction = createTransaction();
    collection1.initializeStepOne(myWorkspace, transaction);
    collection2.initializeStepOne(myWorkspace, transaction);
    collection1.initializeStepTwo();
    collection2.initializeStepTwo();
    transaction.commitUnsafe();
    com.almworks.api.database.util.Singleton[] singletons
      = new Singleton[]{collection1.S1, collection2.S1, collection1.S2, collection2.S2};
    assertEquality(singletons, singletons);
  }

  private void assertEquality(Singleton[] array1, Singleton[] array2) {
    for (int i = 0; i < array1.length; i++) {
      Singleton singleton1 = array1[i];
      for (int j = 0; j < array2.length; j++) {
        Singleton singleton2 = array2[j];
        assertEquals(singleton1, singleton2);
        assertEquals(singleton2, singleton1);
        assertEquals(singleton1.getArtifact(), singleton2.getArtifact());
        assertEquals(singleton2.getArtifact(), singleton1.getArtifact());
      }
    }
  }

  private class SimpleCollection extends SingletonCollection {
    public final Singleton S1 = singleton("1", new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myAttributeOne, "a1");
      }
    });
    public final Singleton S2 = singleton("2", new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myAttributeOne, "a2");
      }
    });
  }

  private class DoubleCollection extends SingletonCollection {
    public final Singleton S1 = singleton("1", new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myAttributeOne, "a1");
      }
    });
    public final Singleton S2 = singleton("1", new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myAttributeOne, "a1");
      }
    });
  }
}
