package com.almworks.api.application;

import com.almworks.api.engine.Connection;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collection;

/**
 * @author : Dyoma
 */
public abstract class SystemKey<T> extends AbstractModelKey<T> {
  public SystemKey(String name) {
    super(name);
  }

  public <SM>SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    assert false : getName();
    return null;
  }

  public void addChanges(UserChanges changes) {
  }

  public void setValue(PropertyMap values, T value) {
    assert false : getName();
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    super.setValue(to, getValue(from));
  }

  public boolean isSystem() {
    return true;
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    throw TODO.shouldNotHappen("No column for system keys");
  }

  public boolean isExportable(Collection<Connection> connections) {
    return false;
  }

  public ModelMergePolicy getMergePolicy() {
    return new ModelMergePolicy.AbstractPolicy() {
      public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
        return false;
      }

      public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
        assert getValue(model).equals(getValue(base));
        assert getValue(model).equals(getValue(newLocal));
      }
    };
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.ALWAYS;
  }

  public static <T> SystemKey constant(String name, final T value) {
    return new SystemKey<T>(name) {
      public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
        throw new UnsupportedOperationException();
      }

      public T getValue(ModelMap model) {
        return value;
      }
    };
  }
}
