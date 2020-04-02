package com.almworks.api.application;

import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;

/**
 * @author dyoma
 */
public interface ModelMergePolicy {
  /**
   * @return true if auto merged all changes and user should not see this key
   */
  boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch);

  void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newValues);

  /**
   * @param isResolved if true, the user sees the row with filter off (when conflict is resolved)
   */
  void renderMergeState(ModelKey<?> key, CellState state, Canvas canvas, boolean isResolved, PropertyMap values);

  void applyResolution(ModelKey<?> key, ModelMap model, PropertyMap values);

  ModelMergePolicy IGNORE = new AbstractPolicy() {
    public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
      return true;
    }

    public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
    }
  };

  ModelMergePolicy MANUAL = new AbstractPolicy() {
    public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
      return false;
    }

    public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
      assert key.isEqualValue(base, newLocal) : key;
    }
  };

  ModelMergePolicy IGNORE_EQUAL = new IgnoreEqual();

  ModelMergePolicy COPY_VALUES = new AbstractPolicy() {
    public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
      key.copyValue(model, branch);
      return true;
    }

    public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
      key.copyValue(model, newLocal);
    }
  };

  ModelMergePolicy NO_MERGE = new AbstractPolicy() {
    public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
      return true;
    }

    public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
      key.copyValue(model, newLocal);
    }
  };

  abstract class AbstractPolicy implements ModelMergePolicy {
    @Override
    public void renderMergeState(ModelKey<?> key, CellState state, Canvas canvas, boolean isResolved, PropertyMap values) {
      key.getRenderer().renderStateOn(state, canvas, values);
    }

    @Override
    public void applyResolution(ModelKey<?> key, ModelMap model, PropertyMap values) {
      key.copyValue(model, values);
    }
  }


  public static class IgnoreEqual extends AbstractPolicy {
    public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
      if (key.isEqualValue(model, branch)) {
        key.copyValue(model, branch);
        return true;
      }
      return false;
    }

    public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
      if (key.isEqualValue(model, newLocal))
        key.copyValue(model, newLocal);
    }
  }
}
