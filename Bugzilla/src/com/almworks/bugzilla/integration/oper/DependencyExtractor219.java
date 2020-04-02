package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.bugzilla.integration.oper.js.JSParserAdapter;
import com.almworks.bugzilla.integration.oper.js.JSParserDelegate;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.*;

class DependencyExtractor219 extends DependencyExtractorBase216Plus {
  private final JSParserDelegate myProductFinder = new MyProductFinder();
  private final Map<Integer, String> myProductIdFirstMap = Collections15.hashMap();
  private final Map<Integer, String> myProductLiteralFirstMap = Collections15.hashMap();
  private final Map<Integer, List<String>> myClassificationMap = Collections15.hashMap();

  protected boolean updateInfo(BugzillaLists info) {
    if (myProductIdFirstMap.isEmpty() && myProductLiteralFirstMap.isEmpty()) {
      return false;
    }

    Map<Integer, String> map;
    if (!myProductLiteralFirstMap.isEmpty()) {
      map = myProductLiteralFirstMap;
      // myProductIdFirstMap and probably myClassificationMap contain classification info
    } else {
      map = myProductIdFirstMap;
    }
    int count = map.size();

    List<String> products = info.getStringList(BugzillaAttribute.PRODUCT);
    
    if (count != products.size()) {
      Log.warn("CANNOT GET DEPENDENCIES 1: " + count + " vs " + products.size() + " " + products);
      return false;
    }

    boolean validComponents = myComponents.size() == count;
    boolean validVersions = myVersions.size() == count;
    boolean validMilestones = myMilestones.size() == count;
    if (!validComponents && !validVersions) {
      return false;
    }

    Log.debug("DE: validComponents: " + validComponents + "; validVersions: " + validVersions + "; validMilestones: " + validMilestones);
    for (Iterator<Integer> ii = map.keySet().iterator(); ii.hasNext();) {
      Integer id = ii.next();
      String product = map.get(id);
      if (!products.contains(product)) {
        Log.warn("products mismatch - no " + product);
        continue;
      }
      storeDependency(info, product, BugzillaAttribute.COMPONENT, myComponents.get(id));
      storeDependency(info, product, BugzillaAttribute.VERSION, myVersions.get(id));
      storeDependency(info, product, BugzillaAttribute.TARGET_MILESTONE, myMilestones.get(id));
    }

    return true;
  }

  protected void clear() {
    myProductIdFirstMap.clear();
    myProductLiteralFirstMap.clear();
    myClassificationMap.clear();
    super.clear();
  }

  protected boolean processInitialIdentifier(String identifier) {
    if ("prods".equalsIgnoreCase(identifier)) {
      myDelegator.setDelegate(myProductFinder);
      return true;
    } else {
      return super.processInitialIdentifier(identifier);
    }
  }

  private class MyProductFinder extends JSParserAdapter {
    private Boolean myIdFirst;
    private String myString;
    private int myId;
    private int myStep;
    private List<String> myClassificationList;

    public void onDelegated() {
      myStep = 0;
      myId = -1;
      myIdFirst = null;
      myString = null;
    }

    public void visitStatementEnd() {
      if (myStep != 99)
        fallback();
      else
        success();
    }

    public void visitNumberSequence(String numbers) {
      if (myStep == 1) {
        myId = getInt(numbers);
        if (myId >= 0) {
          myIdFirst = Boolean.TRUE;
          myStep = 20001;
        } else {
          fallback();
        }
      } else if (myStep == 3 && myIdFirst == Boolean.FALSE) {
        myId = getInt(numbers);
        if (myId < 0)
          fallback();
        myStep = 99;
      } else {
        super.visitNumberSequence(numbers);
      }
    }

    public void visitSpecialChar(char c) {
      if (c == ';')
        return;
      else if (myStep == 0 && c == '[')
        myStep = 1;
      else if (myStep == 20001 && c == ']')
        myStep = 2;
      else if (myStep == 2 && c == '=')
        myStep = 3;
      else if (myStep == 3 && c == '[')
        myStep = 4;
      else if (myStep == 5 && c == ']')
        myStep = 99;
      else if (myStep == 5 && c == ',' && myIdFirst == Boolean.TRUE) {
        if (myClassificationList == null) myClassificationList = Collections15.arrayList();
        myClassificationList.add(myString);
        myString = null;
        myStep = 50001;
      } else
        super.visitSpecialChar(c);
    }

    public void visitStringLiteral(String literal) {
      if (myStep == 1) {
        myString = literal;
        myIdFirst = Boolean.FALSE;
        myStep = 20001;
      } else if (myStep == 4 && myIdFirst == Boolean.TRUE) {
        myString = literal;
        myStep = 5;
      } else if (myStep == 50001 && myIdFirst == Boolean.TRUE) {
        assert myClassificationList != null;
        if (myClassificationList != null) myClassificationList.add(literal);
        myStep = 5;
      } else
        super.visitStringLiteral(literal);
    }

    @Override
    public void visitIdentifier(String identifier) {
      if (myStep == 99) {
        success();
        myDelegator.visitIdentifier(identifier);
      } else {
        super.visitIdentifier(identifier);
      }
    }

    protected <T> void visitToken(TokenType<T> type, T value) {
      fallback();
    }

    private void success() {
      if (myId >= 0) {
        if (myString != null) {
          Map<Integer, String> map = myIdFirst == Boolean.TRUE ? myProductIdFirstMap : myProductLiteralFirstMap;
          String oldProduct = map.put(myId, myString);
          if (oldProduct != null) {
            if (!oldProduct.equals(myString))
              Log.warn("product " + oldProduct + " overridden with " + myString);
          }
        } else if (myClassificationList != null) {
          List<String> old = myClassificationMap.put(myId, myClassificationList);
          if (old != null) {
            if (!old.equals(myClassificationList))
              Log.warn(old + " => " + myClassificationList);
          }
          myClassificationList = null;
        }
      }
      fallback();
    }
  }
}
