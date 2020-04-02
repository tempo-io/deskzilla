package com.almworks.database.schema;


import com.almworks.api.database.*;
import com.almworks.api.database.util.Initializer;
import com.almworks.api.database.util.Singleton;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SchemaTypes /*extends SingletonCollection */ extends SystemSingletonCollection
  implements SystemObjects.TYPE {
  public final com.almworks.api.database.util.Singleton generic;
  public final Singleton named;
  public final Singleton type;
  public final Singleton attribute;
  public final Singleton valueType;
  public final Singleton externalConnection;
  public final Singleton externalArtifact;
  public final Singleton noType;

  public SchemaTypes(final Basis basis) {
    super(basis);
    generic = singleton(GENERIC, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.superType, generic);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "-generic");
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.type,
        });
        myCreator.setValue(myBasis.ATTRIBUTE.optionalAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.isSystemObject, myBasis.ATTRIBUTE.id,
        });
      }
    });

    named = singleton(NAMED, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.superType, generic);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "-named");
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.name,
        });
        myCreator.setValue(myBasis.ATTRIBUTE.optionalAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.displayableName,
        });
      }
    });

    type = singleton(TYPE, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.superType, named);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "type");
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.superType,
          myBasis.ATTRIBUTE.requiredAttributes,
        });
        myCreator.setValue(myBasis.ATTRIBUTE.optionalAttributes, new ArtifactPointer[]{
          myBasis.ATTRIBUTE.optionalAttributes,
          myBasis.ATTRIBUTE.isPrimaryType,
        });
      }
    });

    attribute = singleton(ATTRIBUTE, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.superType, named);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "attribute");
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes,
          new ArtifactPointer[]{
            myBasis.ATTRIBUTE.valueType,
          });
        myCreator.setValue(myBasis.ATTRIBUTE.optionalAttributes,
          new ArtifactPointer[]{
            myBasis.ATTRIBUTE.linkTargetType,
          });
      }
    });

    valueType = singleton(VALUE_TYPE, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "value type");
        myCreator.setValue(myBasis.ATTRIBUTE.superType, named);
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes,
          new ArtifactPointer[]{
          });
      }
    });

    noType = singleton("tNoType", new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "no type");
        myCreator.setValue(myBasis.ATTRIBUTE.superType, generic);
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes,
          new ArtifactPointer[]{
          });
      }
    });

    externalConnection = singleton(EXTERNAL_CONNECTION, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "external connection");
        myCreator.setValue(myBasis.ATTRIBUTE.superType, generic);
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes,
          new ArtifactPointer[]{
          });
      }
    });

    externalArtifact = singleton(EXTERNAL_ARTIFACT, new Initializer() {
      public void initialize(RevisionCreator myCreator) {
        myCreator.setValue(myBasis.ATTRIBUTE.type, type);
        myCreator.setValue(myBasis.ATTRIBUTE.name, "external artifact");
        myCreator.setValue(myBasis.ATTRIBUTE.superType, generic);
        myCreator.setValue(myBasis.ATTRIBUTE.requiredAttributes,
          new ArtifactPointer[]{
            myBasis.ATTRIBUTE.connection,
          });
      }
    });
  }
}
