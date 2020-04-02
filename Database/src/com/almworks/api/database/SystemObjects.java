package com.almworks.api.database;

import com.almworks.api.database.typed.*;
import org.almworks.util.TypedKey;
import org.almworks.util.TypedKeyRegistry;

public interface SystemObjects {
  public static interface ATTRIBUTE {
    AttributeKey<String> ID = AttributeKey.create(NM.A("id"), String.class, Reg.attributeRegistry);
    AttributeKey<String> NAME = AttributeKey.create(NM.A("name"), String.class, Reg.attributeRegistry);
    AttributeKey<String> DISPLAYABLE_NAME =
      AttributeKey.create(NM.A("displayableName"), String.class, Reg.attributeRegistry);
    AttributeKey<ValueTypeDescriptor> VALUETYPE =
      AttributeKey.create(NM.A("valuetype"), ValueTypeDescriptor.class, Reg.attributeRegistry);
    AttributeKey<Boolean> DELETED = AttributeKey.create(NM.A("deleted"), Boolean.class, Reg.attributeRegistry);
    AttributeKey<ArtifactType> TYPE = AttributeKey.create(NM.A("type"), ArtifactType.class, Reg.attributeRegistry);
    AttributeKey<ArtifactType> SUPER_TYPE =
      AttributeKey.create(NM.A("superType"), ArtifactType.class, Reg.attributeRegistry);
    AttributeKey<Boolean> IS_SYSTEM_OBJECT =
      AttributeKey.create(NM.A("systemObject"), Boolean.class, Reg.attributeRegistry);
    AttributeKey<Artifact[]> REQUIRED_ATTRIBUTES =
      AttributeKey.create(NM.A("requiredAttributes"), Artifact[].class, Reg.attributeRegistry);
    AttributeKey<Artifact[]> OPTIONAL_ATTRIBUTES =
      AttributeKey.create(NM.A("optionalAttributes"), Artifact[].class, Reg.attributeRegistry);
    AttributeKey<Boolean> IS_PRIMARY_TYPE =
      AttributeKey.create(NM.A("isPrimaryType"), Boolean.class, Reg.attributeRegistry);
    AttributeKey<ArtifactType> LINK_TARGET_TYPE =
      AttributeKey.create(NM.A("linkTargetType"), ArtifactType.class, Reg.attributeRegistry);
    // todo change ID from "provider" to "connection" - migration
    AttributeKey<Artifact> CONNECTION = AttributeKey.create(NM.A("provider"), Artifact.class, Reg.attributeRegistry);

    /**
     * This attribute is TRUE for "prototype" objects, that is, used to create other objects by copying content
     * and further modifications. This attribute should be removed from object after copying.
     */
    AttributeKey<Boolean> IS_PROTOTYPE = AttributeKey.create(NM.A("isPrototype"), Boolean.class, Reg.attributeRegistry);
    AttributeKey<Artifact> PRIMARY_ARTIFACT =
      AttributeKey.create(NM.A("primaryArtifact"), Artifact.class, Reg.attributeRegistry);

    /**
     * This attribute is used universally by all providers. The integer flag means the "download state":
     * 0 or absent - state unknown (assume QUICK)
     * 1 - dummy artifact, no actual data besides ID, has been created because other artifact needs to link to it
     * 2 - quickly downloaded
     * 3 - fully downloaded
     * <p/>
     * see ArtifactDownloadStage
     * @deprecated see {@link ItemDownloadStage} in Engine
     */
    @Deprecated
    AttributeKey<Integer> ARTIFACT_DOWNLOAD_STAGE =
      AttributeKey.create(NM.A("artifactDownloadStage"), Integer.class, Reg.attributeRegistry);
    int ARTIFACT_DOWNLOAD_STAGE_NEW = 0;
    int ARTIFACT_DOWNLOAD_STAGE_DUMMY = 1;
    int ARTIFACT_DOWNLOAD_STAGE_QUICK = 2;
    int ARTIFACT_DOWNLOAD_STAGE_FULL = 3;
    int ARTIFACT_DOWNLOAD_STAGE_STALE = 4;

    /**
     * Used to remember manually changed attributes when marking local chain as merged.
     * DB automatically unsets this attribute on {@link com.almworks.api.database.RCBArtifact#closeLocalChain(Transaction)}
     */
    AttributeKey<Artifact[]> MANUALLY_CHANGED_ATTRIBUTES =
      AttributeKey.create("manuallyChangedAttributes", Artifact[].class, Reg.attributeRegistry);
  }


  public static interface VALUETYPE {
    TypedKey<ValueTypeDescriptor> INTEGER = TypedKey.create(NM.VT("integer"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> INTEGER_ARRAY = TypedKey.create(NM.VT("array:integer"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> DECIMAL = TypedKey.create(NM.VT("decimal"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> PLAINTEXT = TypedKey.create(NM.VT("plaintext"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> REFERENCE = TypedKey.create(NM.VT("reference"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> BOOLEAN = TypedKey.create(NM.VT("boolean"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> REFERENCE_ARRAY = TypedKey.create(NM.VT("array:reference"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> TIMESTAMP = TypedKey.create(NM.VT("timestamp"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> RAW = TypedKey.create(NM.VT("raw"), Reg.valuetypeRegistry);
    TypedKey<ValueTypeDescriptor> PLAINTEXT_ARRAY = TypedKey.create(NM.VT("array:plaintext"), Reg.valuetypeRegistry);
  }


  public static interface TYPE {
    TypedKey<ArtifactType> GENERIC = TypedKey.create(NM.T("generic"), Reg.typeRegistry);
    TypedKey<ArtifactType> NAMED = TypedKey.create(NM.T("named"), Reg.typeRegistry);
    TypedKey<ArtifactType> TYPE = TypedKey.create(NM.T("type"), Reg.typeRegistry);
    TypedKey<ArtifactType> ATTRIBUTE = TypedKey.create(NM.T("attribute"), Reg.typeRegistry);
    TypedKey<ArtifactType> VALUE_TYPE = TypedKey.create(NM.T("valueType"), Reg.typeRegistry);
    // todo migrate rename id
    TypedKey<ArtifactType> EXTERNAL_CONNECTION = TypedKey.create(NM.T("externalProvider"), Reg.typeRegistry);
    TypedKey<ArtifactType> EXTERNAL_ARTIFACT = TypedKey.create(NM.T("externalArtifact"), Reg.typeRegistry);
  }


  static final class NM {
    private NM() {
    }

    private static String A(String attributeID) {
      return "system:attribute:" + attributeID;
    }

    public static String VT(String valueTypeID) {
      return "system:valuetype:" + valueTypeID;
    }

    public static String T(String typeID) {
      return "system:type:" + typeID;
    }
  }


  static final class Reg {
    public static final TypedKeyRegistry<TypedKey<Attribute>> attributeRegistry = TypedKeyRegistry.create();
    public static final TypedKeyRegistry<TypedKey<ValueTypeDescriptor>> valuetypeRegistry = TypedKeyRegistry.create();
    public static final TypedKeyRegistry<TypedKey<ArtifactType>> typeRegistry = TypedKeyRegistry.create();
  }


  static final class Loader {
    public static void loadConstants() {
      boolean r = !ATTRIBUTE.TYPE.equals(TYPE.ATTRIBUTE);
      r = r && !TYPE.TYPE.equals(VALUETYPE.REFERENCE);
      assert r;
    }
  }
}
