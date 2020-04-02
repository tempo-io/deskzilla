package com.almworks.bugzilla.integration.data;

import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;

public class CustomFieldDependencies {
  public static final String UNKNOWN_OPTION = "";

  /**
   * Contains information about visibility and values of the fields. If a field is not
   * present in the map, it's now known whether it has dependencies. If value is null, then
   * it is known that the field does not have dependencies.
   */
  private final Map<String, Dependency> myDependencyMap = Collections15.linkedHashMap();

  private final Source mySource;

  public CustomFieldDependencies(Source source) {
    mySource = source;
  }

  public Dependency getDependency(String field) {
    Dependency d = myDependencyMap.get(field);
    if (d == null) {
      d = new Dependency();
      myDependencyMap.put(field, d);
    }
    return d;
  }

  public Map<String, Dependency> getDependencyMap() {
    return myDependencyMap;
  }

  /**
   * Joins information with the argument. It is presumed that the information is the same, but probably not full
   * in either this and other object.
   */
  public void merge(CustomFieldDependencies other) {
    for (Map.Entry<String, Dependency> e : other.getDependencyMap().entrySet()) {
      getDependency(e.getKey()).join(e.getValue());
    }
  }

  public Source getSource() {
    return mySource;
  }

  public static class Dependency {
    /**
     * If not null, then the visibility of the field depends on the value of this field.
     * It is equal to "cf_*" for custom fields, and to some specific bugzilla fields as well.
     */
    @Nullable
    private String myVisibilityControllerField;

    /**
     * If not null, then myVisibilityControllerField is also not null, and this field contains the
     * values of the controller field that allows the field to be shown. <br/><br/>
     * <span style="background-color: #FF7777; font-weight:bold;">Bugzilla compatibility note:</span> before 4.2, there is only one option per field;
     * starting with 4.2, there are several options, the field is visible if the controller field value contains either of the specified options.
     */
    @Nullable
    private List<String> myVisibilityControllerValues;

    /**
     * If not null, then the available values for this field are dependent on the value of the controller field.
     */
    @Nullable
    private String myValuesControllerField;

    /**
     * If not null, then myValuesControllerField is also not null, and this map defines the
     * dependent values. The field may contain other values which are not dependent on the controller field.
     * <p/>
     * Key = the value of this field
     * Value = the value of the controller field that is required for the key value to be shown
     */
    private final Map<String, String> myControlledValues = Collections15.hashMap();

    private Dependency() {
    }

    @Nullable
    public String getVisibilityControllerField() {
      return myVisibilityControllerField;
    }

    @Nullable
    public List<String> getVisibilityControllerValues() {
      return myVisibilityControllerValues;
    }

    @Nullable
    public String getValuesControllerField() {
      return myValuesControllerField;
    }

    @NotNull
    public Map<String, String> getControlledValues() {
      return myControlledValues;
    }

    public void setVisibilityControllerField(@Nullable String visibilityControllerField) {
      assert visibilityControllerField == null || myVisibilityControllerField == null ||
        Util.equals(visibilityControllerField, myVisibilityControllerField) :
        myVisibilityControllerField + " " + visibilityControllerField;

      myVisibilityControllerField = visibilityControllerField;
    }

    public void setVisibilityControllerValues(@Nullable List<String> visibilityControllerValue) {
      assert visibilityControllerValue == null || myVisibilityControllerValues == null ||
        visibilityControllerValue.equals(myVisibilityControllerValues) :
        myVisibilityControllerValues + " " + visibilityControllerValue;

      myVisibilityControllerValues = visibilityControllerValue;
    }

    public void setValuesControllerField(@Nullable String valuesControllerField) {
      assert myValuesControllerField == null || myValuesControllerField.equals(valuesControllerField) :
        myValuesControllerField + " " + valuesControllerField;
      myValuesControllerField = valuesControllerField;
    }

    public void mapControlledValue(String controlledValue, String controllerValue) {
      if(!UNKNOWN_OPTION.equals(controllerValue) || !myControlledValues.containsKey(controlledValue)) {
        String expunged = myControlledValues.put(controlledValue, controllerValue);
        assert expunged == null || UNKNOWN_OPTION.equals(expunged) || expunged.equals(controllerValue) : expunged + " " + controllerValue;
      }
    }

    public void join(Dependency other) {
      if (other.getVisibilityControllerField() != null)
        setVisibilityControllerField(other.getVisibilityControllerField());
      if (other.getVisibilityControllerValues() != null)
        setVisibilityControllerValues(other.getVisibilityControllerValues());
      final String vcf = other.getValuesControllerField();
      if(vcf != null) {
        if(!UNKNOWN_OPTION.equals(vcf) || getValuesControllerField() == null) {
          setValuesControllerField(vcf);
        }
      }
      for(Map.Entry<String, String> e : other.getControlledValues().entrySet()) {
        mapControlledValue(e.getKey(), e.getValue());
      }
    }

    public boolean isEmpty() {
      return myVisibilityControllerField == null && myVisibilityControllerValues == null
        && myValuesControllerField == null && myControlledValues.isEmpty();
    }
  }

  public static enum Source {
    NEW_BUG,
    EXISTING_BUG
  }
}
