package com.almworks.bugzilla.integration.oper.js;

import org.almworks.util.Collections15;
import org.almworks.util.Util;

import java.util.Map;
import java.util.Set;

public class JavascriptArrayMappingExtractor extends JSParserAdapter {
  private final Set<String> myLookFor;
  private final Map<Integer, Map<String, String>> myMapping;

  private int myArrayIndex;
  private String myArrayName;
  private int myStep;

  public JavascriptArrayMappingExtractor(Set<String> arrayNames, Map<Integer, Map<String, String>> result) {
    myLookFor = arrayNames;
    myMapping = result;
    myArrayIndex = -1;
    myArrayName = null;
    myStep = 0;
  }

  @Override
  public void visitIdentifier(String identifier) {
    switch (myStep) {
    case 0:
      if (myLookFor.contains(identifier)) {
        myStep = 1;
        myArrayName = identifier;
        break;
      }
    default:
      clearStep();
    }
  }

  @Override
  public void visitSpecialChar(char c) {
    switch (myStep) {
    case 1:
      if (c == '[') {
        myStep = 2;
        break;
      }
    case 3:
      if (c == ']') {
        myStep = 4;
        break;
      }
    case 4:
      if (c == '=') {
        myStep = 5;
        break;
      }

    default:
      clearStep();
    }
  }

  @Override
  public void visitNumberSequence(String numbers) {
    switch (myStep) {
    case 2:
      int v = Util.toInt(numbers, -1);
      if (v >= 0) {
        myStep = 3;
        myArrayIndex = v;
        break;
      }
    default:
      clearStep();
    }
  }

  @Override
  public void visitStringLiteral(String literal) {
    switch (myStep) {
    case 5:
      assert myArrayIndex >= 0;
      assert myArrayName != null;
      Map<String, String> map = myMapping.get(myArrayIndex);
      if (map == null) {
        map = Collections15.hashMap();
        myMapping.put(myArrayIndex, map);
      }
      map.put(myArrayName, literal);
      clearStep();
      break;
    default:
      clearStep();
    }
  }

  private void clearStep() {
    myStep = 0;
    myArrayName = null;
    myArrayIndex = -1;
  }
}
