package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;

public class SeeAlsoModelKey extends AttributeModelKey<List<String>, List<String>> implements CanvasRenderer<PropertyMap> {
  public SeeAlsoModelKey() {
    super(Bug.attrSeeAlso, BugzillaUtil.getDisplayableFieldName(BugzillaAttribute.SEE_ALSO));
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    List<String> r = itemVersion.getValue(getAttribute());
    values.put(getValueKey(), r == null || r.isEmpty() ? Collections.<String>emptyList() : r);
  }

  public List<String> getValue(ModelMap model) {
    return model.get(getValueKey());
  }

  public boolean hasValue(ModelMap model) {
    List<String> value = getValue(model);
    return value != null && !value.isEmpty();
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    List<String> v1 = getValue(models);
    List<String> v2 = getValue(values);
    return isEqualValue(v1, v2);
  }

  private static boolean isEqualValue(List<String> v1, List<String> v2) {
    final int s1 = v1 == null ? 0 : v1.size();
    final int s2 = v2 == null ? 0 : v2.size();
    if(s1 == 0 && s2 == 0) {
      return true;
    }
    if(s1 == 0 || s2 == 0) {
      return false;
    }

    // suppose we have few items: n^2 is more efficient thatn additional memory
    return v1.containsAll(v2) && v2.containsAll(v1);
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    to.put(getValueKey(), from.get(getValueKey()));
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    to.put(getValueKey(), from.get(getValueKey()));
  }

  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return isEqualValue(values1.get(getValueKey()), values2.get(getValueKey()));
  }

  public int compare(List<String> v1, List<String> v2) {
    int s1 = v1 == null ? 0 : v1.size();
    int s2 = v2 == null ? 0 : v2.size();
    if (s1 < s2) {
      return -1;
    } else if (s1 > s2) {
      return 1;
    }
    assert s1 == s2;
    if (s1 == 0)
      return 0;
    for (int i = 0; i < s1; i++) {
      int diff = Util.NN(v1.get(i)).compareTo(Util.NN(v2.get(i)));
      if (diff != 0)
        return diff;
    }
    return 0;
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    return this;
  }

  public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
    List<String> value = getValue(item);
    if (value != null && !value.isEmpty()) {
      String prefix = "";
      for (String v : value) {
        canvas.appendText(prefix);
        canvas.appendText(v);
        prefix = ", ";
      }
    }
  }

  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    assert false : this;
    return null;
  }

  public void addChanges(UserChanges changes) {
    List<String> r = changes.getNewValue(this);
    if (r == null)
      return;
    if (r.isEmpty())
      changes.getCreator().setValue(getAttribute(), null);
    else
      changes.getCreator().setValue(getAttribute(), r);
  }

  public ModelMergePolicy getMergePolicy() {
    return ModelMergePolicy.COPY_VALUES;
  }
}
