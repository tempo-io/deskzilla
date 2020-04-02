package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.oper.js.JSParserAdapter;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.util.List;
import java.util.Map;

public abstract class DependencyExtractorBase216Plus extends DependencyExtractor {
  protected final Map<Integer, List<String>> myComponents = Collections15.hashMap();
  protected final Map<Integer, List<String>> myVersions = Collections15.hashMap();
  protected final Map<Integer, List<String>> myMilestones = Collections15.hashMap();

  protected void clear() {
    myComponents.clear();
    myVersions.clear();
    myMilestones.clear();
    myComponentFinder = new ElementFinder216(myComponents);
    myVersionFinder = new ElementFinder216(myVersions);
    myMilestoneFinder = new ElementFinder216(myMilestones);
  }

  private class ElementFinder216 extends JSParserAdapter {
    private final Map<Integer, List<String>> myTargetMap;
    private int myStep;
    private int myProductId;
    private final List<String> myOptions = Collections15.arrayList();

    public ElementFinder216(Map<Integer, List<String>> targetMap) {
      myTargetMap = targetMap;
    }

    public void onDelegated() {
      myStep = 0;
      myProductId = -1;
      myOptions.clear();
    }

    public void visitSpecialChar(char c) {
      if (myStep == 0 && c == '[')
        myStep = 1;
      else if (myStep == 2 && c == ']')
        myStep = 3;
      else if (myStep == 3 && c == '=')
        myStep = 4;
      else if (myStep == 4 && c == '[')
        myStep = 5;
      else if (myStep == 5 && c == ',')
        return;
      else if (myStep == 5 && c == ']')
        success();
      else
        super.visitSpecialChar(c);
    }

    public void visitNumberSequence(String numbers) {
      if (myStep == 1) {
        myProductId = getInt(numbers);
        if (myProductId < 0)
          fallback();
        else
          myStep = 2;
      } else
        super.visitNumberSequence(numbers);
    }

    public void visitStringLiteral(String literal) {
      if (myStep == 5)
        myOptions.add(literal);
      else
        super.visitStringLiteral(literal);
    }

    private void success() {
      if (myProductId >= 0) {
        List<String> oldList = myTargetMap.put(myProductId, Collections15.arrayList(myOptions));
        if (oldList != null)
          Log.warn("overriding " + oldList + " with " + myOptions);
      }
      fallback();
    }

    protected <T> void visitToken(TokenType<T> type, T value) {
      fallback();
    }
  }
}
