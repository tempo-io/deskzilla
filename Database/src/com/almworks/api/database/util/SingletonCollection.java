package com.almworks.api.database.util;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.TypedArtifact;
import org.almworks.util.Collections15;
import util.concurrent.SynchronizedBoolean;

import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SingletonCollection {
  public static final SingletonCollection EMPTY = new SingletonCollection();
  private final List<Singleton> myItems = Collections15.arrayList();
  private final List<SingletonCollection> myChained = Collections15.arrayList();
  private final SynchronizedBoolean myRegistrationClosed = new SynchronizedBoolean(false, myItems);

  private int myInitState = 0;

  public void chain(SingletonCollection chained) {
    if (isEmpty())
      return;
    assert chained != null;
    assert chained != this;
    synchronized (myChained) {
      if (myRegistrationClosed.get())
        throw new IllegalStateException("singleton registration is closed, please attend another collection");
      myChained.add(chained);
    }
  }

  protected final boolean isEmpty() {
    return this == EMPTY;
  }

  /**
   * Initializes, loads, and if necessary creates or changes metadata objects.
   */
  public final void initialize(final Workspace workspace) {
    if (isEmpty())
      return;
    Transaction transaction = workspace.beginTransaction();
    initializeStepOne(workspace, transaction);
    initializeStepTwo();
    transaction.commitUnsafe();
  }

  public final void initializeStepOne(final Transaction transaction, ArtifactView rootView, FilterManager filterManager) {
    if (isEmpty())
      return;
    assert myInitState == 0 : myInitState + " " + this;
    if (myInitState > 0)
      return;
    myRegistrationClosed.commit(false, true);

    final Singleton[] items = getSingletons();
    for (int i = 0; i < items.length; i++)
      items[i].initCreatorStepOne(transaction, rootView, filterManager);

    final SingletonCollection[] chained = getChained();
    for (int i = 0; i < chained.length; i++)
      chained[i].initializeStepOne(transaction, rootView, filterManager);

    myInitState = 1;
  }

  public final void initializeStepOne(final Workspace workspace, final Transaction transaction) {
    initializeStepOne(transaction, workspace.getViews().getRootView(), workspace.getFilterManager());
  }

  public final void initializeStepTwo() {
    if (isEmpty())
      return;
    assert myInitState == 1;

    if (myInitState > 1)
      return;

    if (myInitState == 0)
      throw new IllegalStateException("initializeStepOne must be called first");

    final Singleton[] items = getSingletons();
    for (int i = 0; i < items.length; i++)
      items[i].initValuesStepTwo();

    final SingletonCollection[] chained = getChained();
    for (int i = 0; i < chained.length; i++)
      chained[i].initializeStepTwo();
    myInitState = 2;
  }

  public final void initializeReadOnly(ArtifactView rootView, FilterManager filterManager) throws SingletonNotFoundException {
    if (isEmpty())
      return;
    assert myInitState == 0;
    if (myInitState > 0)
      return;
    myRegistrationClosed.commit(false, true);

    final Singleton[] items = getSingletons();
    for (int i = 0; i < items.length; i++) {
      items[i].initReadOnly(rootView, filterManager);
    }

    final SingletonCollection[] chained = getChained();
    for (int i = 0; i < chained.length; i++)
      chained[i].initializeReadOnly(rootView, filterManager);
    myInitState = 2;
  }

  protected void addSingleton(Singleton<?> singleton) {
    if (isEmpty())
      throw new IllegalStateException("collection is empty");
    assert singleton != null;
    assert myInitState == 0;
    synchronized (myItems) {
      if (myRegistrationClosed.get())
        throw new IllegalStateException("singleton registration is closed, please attend another collection");
      myItems.add(singleton);
    }
  }

  protected SingletonCollection[] getChained() {
    synchronized (myChained) {
      return myChained.toArray(new SingletonCollection[myChained.size()]);
    }
  }

  public <T extends TypedArtifact> Singleton<T> singleton(String ID, Initializer initializer) {
    if (isEmpty())
      throw new IllegalStateException("collection is empty");
    Singleton<T> singleton = new Singleton<T>(ID, initializer);
    addSingleton(singleton);
    return singleton;
  }

  private Singleton[] getSingletons() {
    synchronized (myItems) {
      return myItems.toArray(new Singleton[myItems.size()]);
    }
  }
}

