package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.bugzilla.integration.oper.js.JSParserAdapter;
import org.almworks.util.Collections15;

import java.util.List;
import java.util.Map;

class DependencyExtractor214 extends DependencyExtractor {
  private final Map<String, List<String>> myComponents = Collections15.hashMap();
  private final Map<String, List<String>> myVersions = Collections15.hashMap();
  private final Map<String, List<String>> myMilestones = Collections15.hashMap();

  protected boolean updateInfo(BugzillaLists info) {
    List<String> products = info.getStringList(BugzillaAttribute.PRODUCT);

    int count = products.size();
    if (count == 0)
      return false;
    if (myComponents.size() != count)
      return false;
    if (myVersions.size() != count)
      return false;
    if (myMilestones.size() != count)
      return false;

    for (int i = 0; i < products.size(); i++) {
      String product = products.get(i);
      storeDependency(info, product, BugzillaAttribute.COMPONENT, myComponents.get(product));
      storeDependency(info, product, BugzillaAttribute.VERSION, myVersions.get(product));
      storeDependency(info, product, BugzillaAttribute.TARGET_MILESTONE, myMilestones.get(product));
    }
    return true;
  }

  protected void clear() {
    myComponents.clear();
    myVersions.clear();
    myMilestones.clear();
    myComponentFinder = new ElementFinder214(myComponents);
    myVersionFinder = new ElementFinder214(myVersions);
    myMilestoneFinder = new ElementFinder214(myMilestones);
  }

  private class ElementFinder214 extends JSParserAdapter {
    private final Map<String, List<String>> myTargetMap;
    private int myStep;
    private String myName;
    private String myProductName;

    public ElementFinder214(Map<String, List<String>> targetMap) {
      myTargetMap = targetMap;
    }

    public void onDelegated() {
      myStep = 0;
      myName = null;
      myProductName = null;
    }

    public void visitSpecialChar(char c) {
      if (myStep == 0 && c == '[')
        myStep = 1;
      else if (myStep == 2 && c == ']')
        myStep = 3;
      else if (myStep == 3 && c == '[')
        myStep = 4;
      else if (myStep == 5 && c == '[')
        myStep = 6;
      else if (myStep == 7 && c == ']')
        myStep = 8;
      else if (myStep == 8 && c == '.')
        myStep = 9;
      else if (myStep == 10 && c == ']')
        myStep = 11;
      else if (myStep == 11 && c == '=')
        myStep = 12;
      else
        super.visitSpecialChar(c);
    }

    public void visitStringLiteral(String literal) {
      if (myStep == 1) {
        myName = literal;
        myStep = 2;
      } else if (myStep == 6) {
        if (myName == null || !myName.equals(literal))
          fallback();
        myStep = 7;
      } else if (myStep == 12) {
        myProductName = literal;
        success();
      } else
        super.visitStringLiteral(literal);
    }

    public void visitIdentifier(String identifier) {
      if (myStep == 4)
        myStep = 5;
      else if (myStep == 9 && "length".equalsIgnoreCase(identifier))
        myStep = 10;
      else
        super.visitIdentifier(identifier);
    }

    protected <T> void visitToken(TokenType<T> type, T value) {
      fallback();
    }

    private void success() {
      if (myName != null && myProductName != null) {
        List<String> list = myTargetMap.get(myProductName);
        if (list == null) {
          list = Collections15.arrayList();
          myTargetMap.put(myProductName, list);
        }
        list.add(myName);
      }
      fallback();
    }
  }
}
