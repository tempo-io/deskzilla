package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.*;
import com.almworks.util.cache2.LongKeyed;
import com.almworks.util.collections.MapIterator;
import org.almworks.util.Collections15;

import java.util.*;

public class Transformator {
  private final Basis myBasis;
  private final Map<Long, RevisionImage> myCachedDefaultImages = Collections15.hashMap();

  public Transformator(Basis basis) {
    myBasis = basis;
  }

  public synchronized RevisionImage getImage(Revision revision, RevisionImageTransform transform) {
    if (transform == null) {
      RevisionImage image = myCachedDefaultImages.get(revision.getKey());
      if (image != null)
        return image;
    }

    RevisionImageBuilder builder = null;
    boolean cachedRevision = false;

    Value value = revision.getValue(myBasis.ATTRIBUTE.type);
    Artifact type = value == null ? null : value.getValue(Artifact.class);
    if (type != null) {
      if (type.equals(myBasis.TYPE.type.getArtifact())) {
        builder = new ArtifactTypeImageBuilder(revision);
        cachedRevision = true;
      } else if (type.equals(myBasis.TYPE.attribute.getArtifact())) {
        builder = new AttributeImageBuilder(revision);
        cachedRevision = true;
      }
    }

    if (builder == null) {
      builder = new RevisionImageBuilder(revision);
    }

    if (cachedRevision && transform == null)
      myCachedDefaultImages.put(revision.getKey(), builder);
    buildMap(revision, builder);
    if (transform != null)
      transformMap(builder, transform);
    builder.finishBuilding(revision);
    return builder;
  }

  private void transformMap(RevisionImageBuilder builder, RevisionImageTransform transform) {
    assert transform != null;
    Map newData = Collections15.hashMap();
    for (Iterator<AttributeImage> iterator = builder.myData.keySet().iterator(); iterator.hasNext();) {
      AttributeImage attributeImage = iterator.next();
      Value value = (Value) builder.myData.get(attributeImage);
      Object newKey = transform.transformAttribute(attributeImage, value);
      Object newValue = transform.transformValue(attributeImage, value);
      newData.put(newKey, newValue);
    }
    builder.myData = newData;
  }

  private void buildMap(Revision revision, RevisionImageBuilder builder) {
    MapIterator<ArtifactPointer, Value> iterator = revision.getValues().iterator();
    while (iterator.next()) {
      Artifact keyArtifact = iterator.lastKey().getArtifact();
      Value value = iterator.lastValue();
      if (revision.getArtifact().equals(keyArtifact)) {
        // self
        assert builder instanceof AttributeImageBuilder;
        builder.myData.put(builder, value);
        continue;
      }

      Revision keyRevision = keyArtifact.getLastRevision();
      assert keyRevision != null;
      RevisionImage keyImage = getImage(keyRevision, null);
      builder.myData.put(keyImage, value);
    }
  }

  private static class RevisionImageBuilder extends LongKeyed implements RevisionImage {
    private Map myData = Collections15.hashMap();
    private boolean myDeleted;
    private Artifact myArtifact;
    private String myRevisionToString;

    public RevisionImageBuilder(Revision revision) {
      super(revision.getKey());
    }

    public final Map getData() {
      return myData;
    }

    protected void finishBuilding(Revision revision) {
      myData = Collections.unmodifiableMap(myData);
      myDeleted = revision.isDeleted();
      myArtifact = revision.getArtifact();
      myRevisionToString = revision.toString();
    }

    public boolean isDeleted() {
      return myDeleted;
    }

    public Artifact getArtifact() {
      return myArtifact;
    }

    public long getPointerKey() {
      if (myArtifact == null)
        throw new IllegalStateException("image is not yet built");
      return myArtifact.getPointerKey();
    }

    public String toString() {
      return "I<" + myRevisionToString + ">";
    }

    public <T extends RevisionImage> T getTyped(Class<T> typedImageClass) {
      Object workaround = this;
      return typedImageClass.isAssignableFrom(getClass()) ? (T) workaround : null;
    }
  }

  private class NamedImageBuilder extends RevisionImageBuilder implements NamedArtifactImage {
    private ArtifactTypeImage myTypeImage;
    private boolean myValid;
    private ArtifactType myType;
    private String myName;
    private String myDisplayableName;
    private Attribute myAttributeName;

    public NamedImageBuilder(Revision revision) {
      super(revision);
    }

    protected void finishBuilding(Revision revision) {
      super.finishBuilding(revision);
      NamedArtifact named = revision.getArtifact().getTyped(NamedArtifact.class);
      assert named != null;
      myValid = named == null ? false : named.isValid(revision);
      myType = named == null ? null : named.getType();
      myName = named == null ? null : named.getName();
      myDisplayableName = named == null ? null : named.getDisplayableName();
      myAttributeName = named == null ? null : named.attributeName();

      Revision lastTypeRevision = myType.getArtifact().getLastRevision();
      ArtifactTypeImage typeImage = (ArtifactTypeImage) getImage(lastTypeRevision, null);
      myTypeImage = named == null ? null : typeImage;
    }

    public ArtifactTypeImage getTypeImage() {
      return myTypeImage;
    }

    public boolean isValid(Revision revision) {
      return myValid;
    }

    public ArtifactType getType() {
      return myType;
    }

    public String getName() {
      return myName;
    }

    public String getDisplayableName() {
      return myDisplayableName;
    }

    public Attribute attributeName() {
      return myAttributeName;
    }
  }

  private class AttributeImageBuilder extends NamedImageBuilder implements AttributeImage {
    private ValueType myValueType;
    private ValueTypeDescriptor myValueTypeDescriptor;

    public AttributeImageBuilder(Revision revision) {
      super(revision);
    }

    protected void finishBuilding(Revision revision) {
      super.finishBuilding(revision);

      Attribute attribute = revision.getArtifact().getTyped(Attribute.class);
      assert attribute != null;
      myValueType = attribute == null ? null : attribute.getValueType();
      myValueTypeDescriptor = attribute == null ? null : attribute.getValueTypeDescriptor();
    }

    public ValueType getValueType() {
      return myValueType;
    }

    public ValueTypeDescriptor getValueTypeDescriptor() {
      return myValueTypeDescriptor;
    }

    public String toString() {
      return "I<attribute:" + getName() + ">";
    }
  }

  private class ArtifactTypeImageBuilder extends NamedImageBuilder implements ArtifactTypeImage {
    private Collection<Attribute> myRequiredAttributes;
    private List<AttributeImage> myRequiredAttributeImages = Collections15.arrayList();
    private Collection<Attribute> myOptionalAttributes;
    private List<AttributeImage> myOptionalAttributeImages = Collections15.arrayList();
    private ArtifactType mySuperType;

    public ArtifactTypeImageBuilder(Revision revision) {
      super(revision);
    }

    public Collection<AttributeImage> getRequiredAttributeImages() {
      return myRequiredAttributeImages;
    }

    public Collection<Attribute> getRequiredAttributes() {
      return myRequiredAttributes;
    }

    public Collection<AttributeImage> getOptionalAttributeImages() {
      return myOptionalAttributeImages;
    }

    public Collection<AttributeImage> getAllAttributeImages() {
      Collection<AttributeImage> result = Collections15.arrayList();
      result.addAll(myRequiredAttributeImages);
      result.addAll(myOptionalAttributeImages);
      return result;
    }

    public ArtifactType getSuperType() {
      return mySuperType;
    }

    public Collection<Attribute> getOptionalAttributes() {
      return myOptionalAttributes;
    }

    public Collection<Attribute> getAllAttributes() {
      Collection<Attribute> result = Collections15.arrayList();
      result.addAll(myRequiredAttributes);
      result.addAll(myOptionalAttributes);
      return result;
    }

    protected void finishBuilding(Revision revision) {
      super.finishBuilding(revision);

      ArtifactType type = revision.getArtifact().getTyped(ArtifactType.class);
      assert type != null;
      myRequiredAttributes = type != null ? type.getRequiredAttributes() : Collections15.<Attribute>emptyList();
      myOptionalAttributes = type != null ? type.getOptionalAttributes() : Collections15.<Attribute>emptyList();
      fillAttributesImages(myRequiredAttributes, myRequiredAttributeImages);
      fillAttributesImages(myOptionalAttributes, myOptionalAttributeImages);
      myRequiredAttributeImages = Collections.unmodifiableList(myRequiredAttributeImages);
      myOptionalAttributeImages = Collections.unmodifiableList(myOptionalAttributeImages);

      mySuperType = type == null ? null : type.getSuperType();
    }

    private void fillAttributesImages(Collection<Attribute> attributes, List<AttributeImage> images) {
      for (Iterator<Attribute> iterator = attributes.iterator(); iterator.hasNext();) {
        Attribute attribute = iterator.next();
        Revision lastRevision = attribute.getArtifact().getLastRevision();
        images.add((AttributeImage) getImage(lastRevision, null));
      }
    }

    public String toString() {
      return "I<type:" + getName() + ">";
    }
  }
}
