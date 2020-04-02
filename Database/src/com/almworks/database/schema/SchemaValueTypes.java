package com.almworks.database.schema;


import com.almworks.api.database.*;
import com.almworks.api.database.util.Initializer;
import com.almworks.api.database.util.Singleton;
import com.almworks.database.Basis;
import com.almworks.database.ValueFactory;
import com.almworks.database.value.*;

import java.math.BigDecimal;
import java.util.Date;

public class SchemaValueTypes extends SystemSingletonCollection implements SystemObjects.VALUETYPE {
  public final Singleton integer;
  public final Singleton integerArray;
  public final Singleton bool;
  public final Singleton plainText;
  public final Singleton plainTextArray;
  public final Singleton reference;
  public final Singleton referenceArray;
  public final Singleton timestamp;
  public final Singleton decimal;
  public final Singleton raw;

  public SchemaValueTypes(final Basis basis) {
    super(basis);

    integer = singleton(INTEGER, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "integer");
      }
    });

    integerArray = singleton(INTEGER_ARRAY, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "integerArray");
      }
    });

    decimal = singleton(DECIMAL, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "decimal");
      }
    });

    plainText = singleton(PLAINTEXT, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "text");
      }
    });

    plainTextArray = singleton(PLAINTEXT_ARRAY, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "array of text");
      }
    });

    reference = singleton(REFERENCE, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "reference");
      }
    });

    referenceArray = singleton(REFERENCE_ARRAY, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "array of references");
      }
    });

    bool = singleton(BOOLEAN, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "boolean");
      }
    });

    timestamp = singleton(TIMESTAMP, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "timestamp");
      }
    });

    raw = singleton(RAW, new Initializer() {
      public void initialize(RevisionCreator creator) {
        creator.setValue(myBasis.ATTRIBUTE.type, myBasis.TYPE.valueType);
        creator.setValue(myBasis.ATTRIBUTE.name, "raw");
      }
    });
  }

  /**
   * @deprecated
   * @param valueFactory
   */
  public void installDefaultValueTypes(ValueFactory valueFactory) {
    valueFactory.installType(plainText, new PlainTextValueType(), String.class, HostedString.class, -99);
    valueFactory.installType(plainTextArray, new ArrayValueType(new PlainTextValueType()), String[].class, null, -100);
    valueFactory.installType(integer, new IntegerValueType(), Integer.class, null, -100);
    valueFactory.installType(integerArray, new ArrayValueType(new IntegerValueType()), Integer[].class, null, -100);

    valueFactory.installType(reference, new ReferenceValueType(myBasis), ArtifactPointer.class, null, -100);
    valueFactory.installType(referenceArray, new ArrayValueType(new ReferenceValueType(myBasis)), ArtifactPointer[].class,
      null, -100);
    valueFactory.installType(bool, new BooleanValueType(), Boolean.class, null, -100);
    valueFactory.installType(timestamp, new TimestampValueType(), Date.class, null, -100);
    valueFactory.installType(decimal, new DecimalValueType(), BigDecimal.class, null, -100);
    valueFactory.installType(raw, new RawValueType(), byte[].class, null, -100);
  }

  public void installDefaultValueTypes2(ValueFactory valueFactory) {
    valueFactory.installType(plainText, new PlainTextValueType(), String.class, null, -99);
    valueFactory.installType(plainTextArray, new ArrayValueType2(new PlainTextValueType()), String[].class, null, -100);
    valueFactory.installType(integerArray, new ArrayValueType2(new IntegerValueType2()), Integer[].class, null, -100);
    valueFactory.installType(integer, new IntegerValueType2(), Integer.class, null, -100);
    valueFactory.installType(reference, new ReferenceValueType2(myBasis), ArtifactPointer.class, null, -100);
    valueFactory.installType(referenceArray, new ArrayValueType2(new ReferenceValueType2(myBasis)), ArtifactPointer[].class,
      null, -100);
    valueFactory.installType(bool, new BooleanValueType(), Boolean.class, null, -100);
    valueFactory.installType(timestamp, new TimestampValueType2(), Date.class, null, -100);
    valueFactory.installType(decimal, new DecimalValueType(), BigDecimal.class, null, -100);
  }
}
