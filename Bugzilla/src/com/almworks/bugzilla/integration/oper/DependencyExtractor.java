package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.bugzilla.integration.oper.js.*;
import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.text.ParseException;
import java.util.*;

abstract class DependencyExtractor {
  protected final JSParserDelegator myDelegator = new JSParserDelegator();
  protected final JSParserDelegate myInitialState = new InitialParser();
  private final JSParserDelegate myBlockState = new JSParserAdapter() {
    protected void visitBlockLevel(int level) {
      if (level == 0)
        myDelegator.setDelegate(myInitialState);
    }
  };

  protected JSParserDelegate myComponentFinder;
  protected JSParserDelegate myVersionFinder;
  protected JSParserDelegate myMilestoneFinder;

  public boolean extractDependencies(String script, BugzillaLists info) {
    clear();
    JSParser parser = new JSParser(script);
    myDelegator.setDelegate(myInitialState);

    try {
      parser.visit(myDelegator);
    } catch (ParseException e) {
      Log.warn("unparseable js from server [" + script + "]", e);
    }

    return updateInfo(info);
  }

  protected abstract void clear();

  protected void fallback() {
    myDelegator.setDelegate(myInitialState);
  }

  protected static int getInt(String numbers) {
    try {
      return Integer.parseInt(numbers);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  protected boolean processInitialIdentifier(String identifier) {
   if ("cpts".equalsIgnoreCase(identifier))
     myDelegator.setDelegate(myComponentFinder);
   else if ("vers".equalsIgnoreCase(identifier))
     myDelegator.setDelegate(myVersionFinder);
   else if ("tms".equalsIgnoreCase(identifier))
     myDelegator.setDelegate(myMilestoneFinder);
   else
     return false;
   return true;
 }

  protected boolean storeDependency(BugzillaLists info, String product, BugzillaAttribute attribute, List<String> allowed) {
    if (allowed == null)
      allowed = Collections15.emptyList();

    Collection<String> existing = info.getStringList(attribute);
    List<String> bad = Collections15.arrayList(allowed);
    bad.removeAll(existing);
    if (bad.size() > 0)
      Log.warn("unknown components " + bad);
    allowed.retainAll(existing);

    Map<String, List<String>> target = info.getProductDependencyMap(attribute);
    target.put(product, allowed);
    return true;
  }

  protected abstract boolean updateInfo(BugzillaLists info);

  private class InitialParser extends JSParserAdapter {
    private boolean myStatementStart = false;

    public void visitStatementStart() {
      myStatementStart = true;
    }

    public void visitIdentifier(String identifier) {
      if ("var".equalsIgnoreCase(identifier))
        return;
      if (myStatementStart && processInitialIdentifier(identifier))
        return;
      super.visitIdentifier(identifier);
    }

    protected void visitBlockLevel(int level) {
      if (level > 0)
        myDelegator.setDelegate(myBlockState);
    }

    protected <T> void visitToken(TokenType<T> type, T value) {
      myStatementStart = false;
    }
  }
}
