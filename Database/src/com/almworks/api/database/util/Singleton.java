package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.util.cache2.LongKeyed;
import org.jetbrains.annotations.*;

public class Singleton <T extends TypedArtifact> extends LongKeyed implements ArtifactPointer {
  public static final long SINGLETON_FAKE_KEY = Long.MAX_VALUE - 1;

  protected final String myID;
  private final Initializer myInitializer;
  private RevisionCreator myCreator = null;
  private ArtifactPointer myCreated = null;

  protected Singleton(String ID, Initializer initializer) {
    super(SINGLETON_FAKE_KEY);
    if (ID == null)
      throw new NullPointerException("ID");
    myID = ID;
    myInitializer = initializer;
  }

  protected Singleton(String ID) {
    this(ID, null);
  }

  public long key() {
    if (myCreated == null)
      throw new SingletonNotInitializedException(this + " is not initialized");
    return myCreated.getArtifact().getPointerKey();
  }

  @NotNull
  public Artifact getArtifact() {
    if (myCreated == null)
      throw new SingletonNotInitializedException(this + " is not initialized");
    return myCreated.getArtifact();
  }

  @NotNull
  public long getPointerKey() {
    if (myCreated == null)
      throw new SingletonNotInitializedException(this + " is not initialized");
    return myCreated.getArtifact().getPointerKey();
  }

/*
  public boolean equals(Object obj) {
    return WorkspaceUtils.equals(this, obj);
  }

  public int hashCode() {
    return WorkspaceUtils.hashCode(this);
  }
*/

  public String toString() {
    return "S::" + myID;
  }

  protected RevisionCreator createCreator(Transaction transaction, ArtifactView rootView, FilterManager filterManager) {
    return WorkspaceUtils.singularChange(transaction, rootView, filterManager, SystemObjects.ATTRIBUTE.ID, myID,
      new Initializer() {
        public void initialize(RevisionCreator creator) {
          creator.setValue(SystemObjects.ATTRIBUTE.ID, myID);
        }
      });
  }

  protected void initialize(RevisionCreator creator) {
    if (myInitializer == null)
      throw new UnsupportedOperationException("overrider initialize() method or set initializer");
    myInitializer.initialize(creator);
  }

//  private static DebugDichotomy count = new DebugDichotomy("Singleton.iniCreatorStepOne", "", 100);
  void initCreatorStepOne(Transaction transaction, ArtifactView rootView, FilterManager filterManager) {
    myCreator = createCreator(transaction, rootView, filterManager);
    myCreated = myCreator;
//    count.a();
  }

  void initValuesStepTwo() {
    if (myCreator == null)
      throw new SingletonNotInitializedException(this + " was not initialized");
    initialize(myCreator);
  }

  void initReadOnly(ArtifactView rootView, FilterManager filterManager) throws SingletonNotFoundException {
    if (myCreated != null)
      throw new SingletonNotInitializedException(this + " was initialized");
    myCreated = loadExisting(rootView, filterManager);
  }

  protected ArtifactPointer loadExisting(ArtifactView rootView, FilterManager filterManager)
    throws SingletonNotFoundException {
    Filter filter = filterManager.attributeEquals(SystemObjects.ATTRIBUTE.ID, myID, true);
    Revision revision = rootView.queryLatest(filter);
    if (revision == null)
      throw new SingletonNotFoundException(this);
    return revision;
  }
}
