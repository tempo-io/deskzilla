package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.data.*;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.Iterator;
import java.util.List;

public class FlagsExtractor {
  private static final String FLAG_PREFIX = "flag-";
  private static final String TYPE_PREFIX = "flag_type-";
  private static final String ADDL_PREFIX = "addl. ";
  private final Element mySelect;
  private final String mySelectName;
  private final String myIdPrefix;
  private final String myRequesteePrefix;
  private final String myEmailSuffix;
  private int myId;
  private String myDescription;
  private List<Character> myAllStatuses;
  private String myName;
  private BugzillaUser myRequestee;
  private char myStatus = 0;
  private Boolean myAllowsRequestee = null;

  public FlagsExtractor(Element select, String selectName, String idPrefix, String requesteePrefix, @Nullable String emailSuffix) {
    mySelect = select;
    mySelectName = selectName;
    myIdPrefix = idPrefix;
    myRequesteePrefix = requesteePrefix;
    myEmailSuffix = emailSuffix;
  }

  public static List<FrontPageData.FlagInfo> extractFlagInfo(Element form, String emailSuffix) {
    List<Element> selects = JDOMUtils.searchElements(form, "select");
    List<FrontPageData.FlagInfo> flags = Collections15.arrayList();
    for (Element select : selects) {
      String selectName = JDOMUtils.getAttributeValue(select, "name", null, false);
      if (selectName == null) {
        continue;
      }
      FrontPageData.FlagInfo flagInfo;
      if (selectName.startsWith(TYPE_PREFIX)) {
        flagInfo = extractAddFlag(select, selectName, emailSuffix);
      } else if (selectName.startsWith(FLAG_PREFIX)) {
        flagInfo = extractFlagState(select, selectName, emailSuffix);
      } else {
        continue;
      }
      if (flagInfo != null) {
        flags.add(flagInfo);
      }
    }
    return flags;
  }

  @Nullable
  private static FrontPageData.FlagInfo extractFlagState(Element select, String selectName, String emailSuffix) {
    FlagsExtractor extractor = new FlagsExtractor(select, selectName, FLAG_PREFIX, "requestee-", emailSuffix);
    extractor.perform();
    if (extractor.myStatus == 0) {
      Log.warn("No selected status");
      return null;
    }
    return new FrontPageData.FlagInfo(extractor.myId, extractor.myStatus, extractor.myAllStatuses, extractor.myName,
      extractor.myDescription, extractor.myRequestee, extractor.myAllowsRequestee);
  }

  private static FrontPageData.FlagInfo extractAddFlag(Element select, String selectName, String emailSuffix) {
    FlagsExtractor extractor = new FlagsExtractor(select, selectName, TYPE_PREFIX, "requestee_type-", emailSuffix);
    extractor.perform();
    return new FrontPageData.FlagInfo(extractor.myId, extractor.myAllStatuses, extractor.myName, extractor.myDescription, 
      extractor.myAllowsRequestee);
  }

  private void perform() {
    try {
      myId = Integer.parseInt(mySelectName.substring(myIdPrefix.length()));
    } catch (NumberFormatException e) {
      Log.error("Unknown flag syntax " + mySelectName);
      myId = -1;
    }
    myDescription = JDOMUtils.getAttributeValue(mySelect, "title", null, false);
    collectStatuses();
    Element td = mySelect.getParentElement();
    if (!"td".equalsIgnoreCase(td.getName())) {
      Log.error("Unsupported structure (td expected) " + mySelect.getParentElement());
      return;
    }
    Element tr = td.getParentElement();
    if (!"tr".equalsIgnoreCase(tr.getName())) {
      Log.error("Unsupported structure (tr expected) " + mySelect.getParentElement());
      return;
    }
    processLabel(tr);
    processRequestee(tr, myRequesteePrefix);
  }

  private void processRequestee(Element tr, String requesteePrefix) {
    if (myId < 0) {
      return;
    }

    final Element reqInput  = JDOMUtils.searchElement(tr, "input", "id", requesteePrefix + myId);
    if (reqInput != null) {
      myAllowsRequestee = Boolean.TRUE;
      myRequestee = BugzillaUser.shortEmailName(JDOMUtils.getAttributeValue(reqInput, "value", null, false), null, myEmailSuffix);
      return;
    }

    // DZO-695
    final Element reqSelect = JDOMUtils.searchElement(tr, "select", "id", requesteePrefix + myId);
    if (reqSelect != null) {
      myAllowsRequestee = Boolean.TRUE;
      final Iterator<Element> it = JDOMUtils.searchElementIterator(reqSelect, "option");
      while (it.hasNext()) {
        final Element option = it.next();
        if (JDOMUtils.getAttributeValue(option, "selected", null, false) != null) 
          myRequestee = OperUtils.extractShortUserFromOption(option, myEmailSuffix);
      }
      return;
    }

    myAllowsRequestee = Boolean.FALSE;
  }

  private void processLabel(Element tr) {
    final boolean ok = tryExactLabelTag(tr) || tryAllLabelTags(tr) || tryTdTags(tr);
    if(!ok) {
      Log.error("Unsupported structure (flag label not found)");
    }
  }

  private boolean tryExactLabelTag(Element tr) {
    final Element label = JDOMUtils.searchElement(tr, "label", "for", mySelectName);
    if(label != null) {
      getNameFromLabel(label);
      return true;
    }
    return false;
  }

  private boolean tryAllLabelTags(Element tr) {
    final List<Element> labels = JDOMUtils.searchElements(tr, "label");
    return tryMatchByTitle(labels) || trySingleLabel(labels);
  }

  private boolean tryMatchByTitle(List<Element> labels) {
    for(final Element e : labels) {
      if(myDescription != null && myDescription.equals(JDOMUtils.getAttributeValue(e, "title", null, false))) {
        getNameFromLabel(e);
        return true;
      }
    }
    return false;
  }

  private boolean trySingleLabel(List<Element> labels) {
    if(labels.size() == 1) {
      getNameFromLabel(labels.get(0));
      return true;
    }
    return false;
  }

  private void getNameFromLabel(Element label) {
    myName = label.getText();
    if(myDescription == null || myDescription.length() == 0) {
      myDescription = JDOMUtils.getAttributeValue(label, "title", null, false);
    }
  }

  private boolean tryTdTags(Element tr) {
    final List<Element> tds = JDOMUtils.searchElements(tr, "td");
    return tryFourTds(tds) || tryThreeTds(tds);
  }

  private boolean tryFourTds(List<Element> tds) {
    if(tds.size() == 4) {
      getNameFromTd(tds.get(1));
      return true;
    }
    return false;
  }

  private boolean tryThreeTds(List<Element> tds) {
    if(tds.size() == 3) {
      getNameFromTd(tds.get(0));
      return true;
    }
    return false;
  }

  private void getNameFromTd(Element td) {
    myName = td.getTextTrim();
    if(myName.startsWith(ADDL_PREFIX)) {
      myName = myName.substring(ADDL_PREFIX.length());
    }
  }

  private void collectStatuses() {
    assert myAllStatuses == null;
    myAllStatuses = Collections15.arrayList();
    Iterator<Element> it = JDOMUtils.searchElementIterator(mySelect, "option");
    while (it.hasNext()) {
      Element option = it.next();
      char st = BugInfo.Flag.parseStatus(JDOMUtils.getAttributeValue(option, "value", null, false));
      if (st == 0) continue;
      boolean selected = JDOMUtils.getAttributeValue(option, "selected", null, false) != null;
      myAllStatuses.add(st);
      if (selected) myStatus = st;
    }
  }
}
