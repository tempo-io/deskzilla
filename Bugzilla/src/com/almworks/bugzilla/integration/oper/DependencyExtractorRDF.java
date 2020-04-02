package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;

import java.util.List;

public class DependencyExtractorRDF extends DependencyExtractor {
  private final BugzillaRDFConfig myRdfConfig;

  public DependencyExtractorRDF(BugzillaRDFConfig rdfConfig) {
    myRdfConfig = rdfConfig;
  }

  @Override
  protected void clear() {}

  @Override
  public boolean extractDependencies(String script, BugzillaLists info) {
    return updateInfo(info);
  }

  @Override
  protected boolean updateInfo(BugzillaLists info) {
    if(myRdfConfig == null) {
      return false;
    }

    final ProductDependencies deps = myRdfConfig.getProductDependencies();
    if(deps == null || deps.isEmpty()) {
      return false;
    }

    for(final String product : deps.getProducts()) {
      storeDependency(info, product, BugzillaAttribute.COMPONENT, deps.getComponents(product));
      storeDependency(info, product, BugzillaAttribute.VERSION, deps.getVersions(product));
      final List<String> milestones = deps.getMilestones(product);
      if(milestones != null) {
        storeDependency(info, product, BugzillaAttribute.TARGET_MILESTONE, milestones);
      }
    }
    return true;
  }
}
