package com.almworks.bugzilla.integration.data;

import com.almworks.util.collections.MultiMap;
import org.jetbrains.annotations.*;

import java.util.List;

public class ProductDependencies {
  private final List<String> myProducts;
  private final MultiMap<String, String> myComponents;
  private final MultiMap<String, String> myVersions;
  private final MultiMap<String, String> myMilestones;

  public ProductDependencies(
    List<String> products, MultiMap<String, String> components,
    MultiMap<String, String> versions, MultiMap<String, String> milestones)
  {
    myProducts = products;
    myComponents = components;
    myVersions = versions;
    myMilestones = milestones;
  }

  public boolean isEmpty() {
    return myProducts.isEmpty();
  }

  public List<String> getProducts() {
    return myProducts;
  }

  @Nullable
  public List<String> getComponents(String product) {
    return myComponents.getWritableCopyOrNull(product);
  }

  @Nullable
  public List<String> getVersions(String product) {
    return myVersions.getWritableCopyOrNull(product);
  }

  @Nullable
  public List<String> getMilestones(String product) {
    return myMilestones.getWritableCopyOrNull(product);
  }
}
