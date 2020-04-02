package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.util.collections.MultiMap;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.arrayList;
import static org.almworks.util.Collections15.hashMap;

public class BugzillaRDFConfig {
  private final List<CustomFieldInfo> myCustomFieldInfos = arrayList();
  private final List<StatusInfo> myStatusInfos = arrayList();
  private final Map<String, String> myKeywords = hashMap();
  private String myInstallVersion;
  private ProductDependencies myProductDependencies;

  public void addCustomField(CustomFieldInfo cfi) {
    myCustomFieldInfos.add(cfi);
  }

  public void addStatusInfo(String name, @Nullable Boolean isOpen) {
    myStatusInfos.add(new StatusInfo(name, isOpen));
  }

  public void setInstallVersion(String installVersion) {
    myInstallVersion = installVersion;
  }

  public void setProductDependencies(
    List<String> products, MultiMap<String, String> components,
    MultiMap<String, String> versions, MultiMap<String, String> milestones)
  {
    myProductDependencies = new ProductDependencies(products, components, versions, milestones);
  }

  public List<CustomFieldInfo> getCustomFieldInfos() {
    return myCustomFieldInfos;
  }

  @NotNull
  public List<StatusInfo> getStatusInfos() {
    return myStatusInfos;
  }

  @NotNull
  public Map<String, String> getKeywords() {
    return Collections.unmodifiableMap(myKeywords);
  }

  public String getInstallVersion() {
    return myInstallVersion;
  }

  public boolean isCustomFieldDataReliable() {
    if (myInstallVersion == null) return false;
    return BugzillaIntegration.isVersionOrLater(myInstallVersion, "3.2");
  }

  @Nullable
  public ProductDependencies getProductDependencies() {
    return myProductDependencies;
  }

  public void addKeywords(String name, String description) {
    myKeywords.put(name, description);
  }
}
