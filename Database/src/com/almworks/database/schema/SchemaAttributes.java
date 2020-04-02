package com.almworks.database.schema;


import com.almworks.api.database.RevisionCreator;
import com.almworks.api.database.SystemObjects;
import com.almworks.api.database.util.Initializer;
import com.almworks.api.database.util.Singleton;
import com.almworks.database.Basis;

public class SchemaAttributes extends SystemSingletonCollection implements SystemObjects.ATTRIBUTE {
  public final Singleton type;
  public final Singleton superType;
  public final Singleton name;
  public final Singleton requiredAttributes;
  public final Singleton optionalAttributes;
  public final Singleton valueType;
  public final Singleton deleted;
  public final Singleton id;
  public final Singleton isSystemObject;
  public final Singleton isPrimaryType;
  public final Singleton displayableName;
  public final Singleton linkTargetType;
  public final Singleton connection;
  public final Singleton isPrototype;
  public final Singleton primaryArtifact;
  public final Singleton artifactDownloadStage;
  public final Singleton manuallyChangedAttributes;

  public SchemaAttributes(final Basis basis) {
    super(basis);
    type = singleton(TYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "type");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.type);
      }
    });

    superType = singleton(SUPER_TYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "superType");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.type);
      }
    });

    name = singleton(NAME, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "name");
        creator.setValue(valueType, myBasis.VALUETYPE.plainText);
      }
    });

    requiredAttributes = singleton(REQUIRED_ATTRIBUTES, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "requiredAttributes");
        creator.setValue(valueType, myBasis.VALUETYPE.referenceArray);
        creator.setValue(linkTargetType, myBasis.TYPE.attribute);
      }
    });

    optionalAttributes = singleton(OPTIONAL_ATTRIBUTES, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "optionalAttributes");
        creator.setValue(valueType, myBasis.VALUETYPE.referenceArray);
        creator.setValue(linkTargetType, myBasis.TYPE.attribute);
      }
    });

    valueType = singleton(VALUETYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "valueType");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.valueType);
      }
    });

    linkTargetType = singleton(LINK_TARGET_TYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "linkTargetType");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.type);
      }
    });

    deleted = singleton(DELETED, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "deleted");
        creator.setValue(valueType, myBasis.VALUETYPE.bool);
      }
    });

    id = singleton(ID, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "ID");
        creator.setValue(valueType, myBasis.VALUETYPE.plainText);
      }
    });

    isSystemObject = singleton(IS_SYSTEM_OBJECT, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "systemObject");
        creator.setValue(valueType, myBasis.VALUETYPE.bool);
      }
    });

    isPrimaryType = singleton(IS_PRIMARY_TYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "isPrimaryType");
        creator.setValue(valueType, myBasis.VALUETYPE.bool);
      }
    });

    displayableName = singleton(DISPLAYABLE_NAME, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "displayableName");
        creator.setValue(valueType, myBasis.VALUETYPE.plainText);
      }
    });

    connection = singleton(CONNECTION, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "connection");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.externalConnection);
      }
    });

    isPrototype = singleton(IS_PROTOTYPE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "isPrototype");
        creator.setValue(valueType, myBasis.VALUETYPE.bool);
      }
    });

    primaryArtifact = singleton(PRIMARY_ARTIFACT, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "primaryArtifact");
        creator.setValue(valueType, myBasis.VALUETYPE.reference);
        creator.setValue(linkTargetType, myBasis.TYPE.externalArtifact);
      }
    });

    artifactDownloadStage = singleton(ARTIFACT_DOWNLOAD_STAGE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "artifactDownloadStage");
        creator.setValue(valueType, myBasis.VALUETYPE.integer);
      }
    });

    manuallyChangedAttributes = singleton(MANUALLY_CHANGED_ATTRIBUTES, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(type, myBasis.TYPE.attribute);
        creator.setValue(name, "manuallyChangedAttributes");
        creator.setValue(valueType, myBasis.VALUETYPE.referenceArray);
        creator.setValue(linkTargetType, myBasis.TYPE.attribute);
      }
    });
  }
}
