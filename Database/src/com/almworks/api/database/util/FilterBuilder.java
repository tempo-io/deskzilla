package com.almworks.api.database.util;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.database.*;
import com.almworks.api.database.typed.AttributeKey;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * @author dyoma
 */
public class FilterBuilder {
  private final Workspace myWorkspace;
  private final List<Filter> myPrimes = Collections15.arrayList();
  private final List<FilterBuilder> myComposites = Collections15.arrayList();
  private final boolean myAnd;

  public FilterBuilder(Workspace workspace, boolean and) {
    myWorkspace = workspace;
    myAnd = and;
  }

  public Filter create() {
    Filter result = null;
    for (Filter prime : myPrimes)
      result = result == null ? prime : compose(result, prime);
    for (FilterBuilder composite : myComposites) {
      Filter filter = composite.create();
      result = result == null ? filter : compose(result, filter);
    }
    return result != null ? result : Filter.ALL;
  }

  public boolean isEmpty() {
    return myPrimes.isEmpty() && myComposites.isEmpty();
  }

  public FilterBuilder and() {
    return createChild(true);
  }

  public FilterBuilder or() {
    return createChild(false);
  }

  private FilterBuilder createChild(boolean and) {
    if (myAnd == and)
      return this;
    FilterBuilder result = new FilterBuilder(myWorkspace, and);
    myComposites.add(result);
    return result;
  }

  private Filter compose(Filter result, Filter filter) {
    return myAnd ? getFilterManager().and(result, filter) : getFilterManager().or(result, filter);
  }

  public static FilterBuilder createAnd(ComponentContainer container) {
    Workspace workspace = container.getActor(Workspace.ROLE);
    assert workspace != null : container;
    return create(workspace, true);
  }

  public static FilterBuilder create(Workspace workspace, boolean and) {
    return new FilterBuilder(workspace, and);
  }

  public static FilterBuilder createAnd(Workspace workspace) {
    return create(workspace, true);
  }

  public void sameConnection(ArtifactPointer artifact) {
    Revision revision = artifact.getArtifact().getLastRevision();
    ArtifactPointer connection = revision.getValue(SystemObjects.ATTRIBUTE.CONNECTION, ArtifactPointer.class);
    if (connection == null) {
      assert false : artifact;
    } else {
      connection(connection);
    }
  }

  public void connection(ArtifactPointer connection) {
    AttributeKey<Artifact> attribute = SystemObjects.ATTRIBUTE.CONNECTION;
    equals(attribute, connection, true);
  }

  public void equals(AttributeKey<?> attribute, Object value, boolean indexable) {
    myPrimes.add(getFilterManager().attributeEquals(attribute, value, indexable));
  }

  public void equals(ArtifactPointer attribute, Object value, boolean indexable) {
    myPrimes.add(getFilterManager().attributeEquals(attribute, value, indexable));
  }

  public FilterManager getFilterManager() {
    return myWorkspace.getFilterManager();
  }

  public void add(@Nullable Filter filter) {
    if (filter == null) return;
    myPrimes.add(filter);
  }

  public FilterBuilder type(ArtifactPointer type, boolean indexable) {
    equals(SystemObjects.ATTRIBUTE.TYPE, type, indexable);
    return this;
  }

  public Collection<Revision> getAllUserArtifacts() {
    return createView().getAllArtifacts();
  }

  public ArtifactView createView() {
    return myWorkspace.getViews().getUserView().changeStrategy(RevisionAccess.ACCESS_MAINCHAIN).filter(create());
  }

  public Revision queryLatest() {
    return createView().queryLatest();
  }

  public Revision queryLatest(Filter filter) {
    return createView().queryLatest(filter);
  }

  public FilterBuilder nestAnd() {
    FilterBuilder result = new FilterBuilder(myWorkspace, true);
    myComposites.add(result);
    return result;
  }

  public int count() {
    return createView().count();
  }
}
