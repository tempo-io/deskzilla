package com.almworks.database.objects;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.Attribute;
import com.almworks.api.database.typed.AttributeKey;
import com.almworks.api.universe.Atom;
import com.almworks.database.Basis;
import com.almworks.util.cache2.LongKeyed;
import com.almworks.util.collections.MapIterator;
import com.almworks.util.collections.MapSource;
import com.almworks.util.threads.Threads;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RevisionDecorator extends LongKeyed implements RevisionWithInternals, MapSource<ArtifactPointer, Value> {

  private final Basis myBasis;
  private final RevisionRefined myRevisionRefined;

  public RevisionDecorator(Basis basis, RevisionRefined revisionRefined) {
    super(revisionRefined.getKey());
    assert basis != null;
    assert revisionRefined != null;
    myBasis = basis;
    myRevisionRefined = revisionRefined;
  }

  public long getPointerKey() {
    return getArtifact().getKey();
  }

  public <K> K getAspect(TypedKey<K> key) {
    return myBasis.ourAspectManager.getAspect(this, key);
  }

  public Map<TypedKey, ?> copyAspects() {
    return myBasis.ourAspectManager.copyAspects(this);
  }

  public Value get(ArtifactPointer pointer) {
    return getValue(pointer);
  }

  public MapIterator<ArtifactPointer, Value> iterator() {
    return myRevisionRefined.iterator();
  }

  @NotNull
  public Artifact getArtifact() {
    return getChain().getArtifact();
  }

  public RevisionChain getChain() {
    Threads.assertLongOperationsAllowed();
    return myRevisionRefined.getChain();
  }

  public RevisionImage getImage() {
    Threads.assertLongOperationsAllowed();
    return myBasis.ourTransformator.getImage(this, null);
  }

  public long getKey() {
    return myRevisionRefined.getKey();
  }

  public Revision getPrevRevision() {
    return myRevisionRefined.getPrevRevision();
  }

  public <T> T getValue(TypedKey<Attribute> systemAttribute, Class<T> valueClass) {
    Attribute attribute = myBasis.getSystemObject(systemAttribute);
    return attribute == null ? null : getValue(attribute, valueClass);
  }

  public <T> T getValue(ArtifactPointer attribute, Class<T> valueClass) {
    Value value = getValue(attribute);
    return value == null ? null : value.getValue(valueClass);
  }

  public <T> T getValue(AttributeKey<T> systemAttribute) {
    return getValue(systemAttribute, systemAttribute.getAttributeClass());
  }

  public Value getValue(TypedKey<Attribute> systemAttribute) {
    return getValue(myBasis.getSystemObject(systemAttribute));
  }

  public Value getValue(ArtifactPointer attribute) {
    Threads.assertLongOperationsAllowed();
    return myRevisionRefined.getValue(attribute);
  }

  public MapSource<ArtifactPointer, Value> getValues() {
    return this;
  }

  public WCN getWCN() {
    return myRevisionRefined.getWCN();
  }

  public long getOrder() {
    return getChain().getRevisionOrder(this);
  }

  public Map<ArtifactPointer, Value> getChanges() {
    return myRevisionRefined.getChanges();
  }

  public boolean isDeleted() {
    return getValue(myBasis.ATTRIBUTE.deleted) != null;
  }

  public boolean isAccessibleIn(@Nullable Transaction t) {
    Atom a = getAtom();
    if (a.isCommitted()) return true;
    if (t != null) {
      return t.isChanging(this);
    }
    return false;
  }

  public Atom getAtom() {
    return myRevisionRefined.getAtom();
  }

  public RevisionInternals getPrevRevisionInternals() {
    return myRevisionRefined.getPrevRevisionInternals();
  }

  public void invalidateValuesCache(RevisionAccess access) {
    myRevisionRefined.invalidateValuesCache(access);
  }

  public void forceCreation() {
    myRevisionRefined.forceCreation();
  }

  public RevisionIterator getRevisionIterator() {
    return myRevisionRefined.getRevisionIterator();
  }
}
