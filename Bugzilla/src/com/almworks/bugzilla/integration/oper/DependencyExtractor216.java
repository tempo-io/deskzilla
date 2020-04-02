package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;

import java.util.List;

class DependencyExtractor216 extends DependencyExtractorBase216Plus {
  protected boolean updateInfo(BugzillaLists info) {
    List<String> products = info.getStringList(BugzillaAttribute.PRODUCT);

    int count = products.size();
    if (count == 0)
      return false;
    if (myComponents.size() != count)
      return false;
    if (myVersions.size() != count)
      return false;
    int milestones = myMilestones.size();
    if (milestones > 0 && milestones != count)
      return false;
    boolean hasMilestones = milestones > 0;

    for (int i = 0; i < products.size(); i++) {
      String product = products.get(i);
      Integer id = i;
      storeDependency(info, product, BugzillaAttribute.COMPONENT, myComponents.get(id));
      storeDependency(info, product, BugzillaAttribute.VERSION, myVersions.get(id));
      if (hasMilestones)
        storeDependency(info, product, BugzillaAttribute.TARGET_MILESTONE, myMilestones.get(id));
    }
    return true;
  }
}
