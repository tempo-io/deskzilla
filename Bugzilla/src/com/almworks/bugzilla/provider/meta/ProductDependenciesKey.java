package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.ProductDependenciesTracker;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collection;

public class ProductDependenciesKey extends AbstractModelKey<ProductDependenciesTracker> {
  private static final String KEY_NAME = "#productDependencies";
  public static final ProductDependenciesKey KEY = new ProductDependenciesKey();

  private  ProductDependenciesKey() {
    super(KEY_NAME);
  }

  public void addChanges(UserChanges changes) {
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    BugzillaConnection conn = itemServices.getConnection(BugzillaConnection.class);
    values.put(getModelKey(), conn == null ? null : conn.getDependenciesTracker());
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    super.copyValue(to, from);
    to.registerKey(KEY_NAME, this);
  }

  public boolean isExportable(Collection<Connection> connections) {
    return false;
  }

  public ModelMergePolicy getMergePolicy() {
    return ModelMergePolicy.IGNORE;
  }

  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    return null;
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    throw TODO.shouldNotHappen("No column for this key");
  }

  public boolean isSystem() {
    return true;
  }
}
