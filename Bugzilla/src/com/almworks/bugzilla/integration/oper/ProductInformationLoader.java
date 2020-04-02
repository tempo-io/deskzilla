package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.ExtractFormParameters;
import com.almworks.api.connector.http.HtmlUtils;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.oper.js.*;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Element;

import java.text.ParseException;
import java.util.*;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableSet;

public class ProductInformationLoader extends BugzillaOperation {
  private final ServerInfo myServerInfo;
  private final String myProduct;

  private static final String JS_COMPONENT = "components";
  private static final String JS_ASSIGNEDTO = "initialowners";
  private static final String JS_QACONTACT = "initialqacontacts";
  private static final String JS_CC = "initialccs";

  private static final Set<String> COMPONENT_DEFAULT_JS_ARRAYS =
    unmodifiableSet(Collections15.hashSet(JS_COMPONENT, JS_ASSIGNEDTO, JS_QACONTACT, JS_CC));

  public ProductInformationLoader(ServerInfo serverInfo,String product) {
    super(serverInfo);
    myServerInfo = serverInfo;
    myProduct = product;
  }

  public BugzillaProductInformation getInfo() throws ConnectorException {
    return runOperation(new RunnableRE<BugzillaProductInformation, ConnectorException>() {
      public BugzillaProductInformation run() throws ConnectorException {
        Element page = loadSubmitPage(myServerInfo.getBaseURL(), myProduct);
        Element form = OperUtils.findSubmitFormElement(page, false);
        BugzillaProductInformation result = new BugzillaProductInformation(myProduct);
        if (form == null) {
          Log.warn("cannot find bug submit form for product [" + myProduct + "]");
          result.setInvalid();
          return result;
        }

        List<Pair<BugGroupData, Boolean>> groups = OperUtils.findGroupInfo(form);
        result.setGroups(groups);

        Element privateDescription = findPrivateDescriptionCheckbox(form);
        result.setDescriptionMayBePrivate(privateDescription != null);

        Pair<Map<String, CustomFieldInfo>, MultiMap<String, String>> pair =
          OperUtils.findCustomFieldInfoAndValues(form, true);
        if (pair != null) {
          result.setCustomFieldInfo(pair.getFirst());
          result.setCustomFieldDefaultValues(pair.getSecond());
        }

        // 1. default values for some fields
        Map<BugzillaAttribute, String> defaultValues = findDefaultValues(form);
        if (defaultValues != null && !defaultValues.isEmpty()) {
          result.setDefaultValues(defaultValues);
        }

        List<BugzillaUser> userList = findUserList(form);
        result.setUserList(userList);

        List<String> initalStatuses = findInitialStatuses(form);
        result.setInitialStatuses(initalStatuses);

        result.setComponentDefaults(findComponentDefaults(form.getDocument().getRootElement()));

        result.setCustomFieldDependencies(
          CustomFieldDependencyExtractor.getDependencies(page, CustomFieldDependencies.Source.NEW_BUG));

        return result;
      }
    });
  }

  private Map<String, ComponentDefaults> findComponentDefaults(Element root) {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(root, "script", "type", "text/javascript");
    while (ii.hasNext()) {
      Element script = ii.next();
      String js = JDOMUtils.getText(script, true);
      try {
        Map<Integer, Map<String, String>> mapping = extractMapping(js);
        if (!mapping.isEmpty()) {
          return transformComponentDefaultsMap(mapping);
        }
      } catch (ParseException e) {
        Log.warn("cannot parse [" + js + "]", e);
      }
    }
    return null;
  }

  static Map<Integer, Map<String, String>> extractMapping(String js) throws ParseException {
    Map<Integer, Map<String, String>> mapping = Collections15.hashMap();
    JSParserAdapter parser = new JavascriptArrayMappingExtractor(COMPONENT_DEFAULT_JS_ARRAYS, mapping);
    new JSParser(js).visit(parser);
    return mapping;
  }

  private Map<String, ComponentDefaults> transformComponentDefaultsMap(Map<Integer, Map<String, String>> mapping) {
    LinkedHashMap<String, ComponentDefaults> r = Collections15.linkedHashMap();
    for (Map<String, String> map : mapping.values()) {
      String c = map.get(JS_COMPONENT);
      if (c == null)
        continue;
      String assignedTo = map.get(JS_ASSIGNEDTO);
      String qaContact = map.get(JS_QACONTACT);
      String cc = map.get(JS_CC);
      if (assignedTo == null && qaContact == null && cc == null)
        continue;
      BugzillaUser assignToUser = BugzillaUser.shortEmailName(assignedTo, null, myServerInfo.getEmailSuffix());

      List<String> cclist = null;
      if (cc != null) {
        cclist = Collections15.arrayList();
        String[] ccssplit = cc.split("[\\s,;]+");
        for (String s : ccssplit) {
          if (s != null && s.length() > 0) {
            cclist.add(s);
          }
        }
      }

      r.put(c, new ComponentDefaults(assignToUser != null ? assignToUser.getEmailId() : null, qaContact, cclist));
    }
    return r;
  }

  private List<String> findInitialStatuses(Element form) {
    // selectable status
    Element select = JDOMUtils.searchElement(form, "select", "name", "bug_status");
    if (select != null) {
      return HtmlUtils.getSelectOptionValues(select);
    }
    // single status
    Element input = JDOMUtils.searchElement(form, "input", "name", "bug_status");
    if (input != null) {
      if (!"hidden".equalsIgnoreCase(JDOMUtils.getAttributeValue(input, "type", "", false))) {
        Log.warn(this + ": strange input " + input);
        return null;
      }
      String status = JDOMUtils.getAttributeValue(input, "value", "", true);
      return status.length() == 0 ? null : singletonList(status);
    }
    // single status, Bugzilla 4.0+
    Element statusContainer = JDOMUtils.searchElement(form, "td", "id", "field_container_bug_status");
    if (statusContainer != null) {
      return singletonList(JDOMUtils.getTextTrim(statusContainer));
    }
    Log.warn("PIL: no initial status info");
    return null;
  }

  private Map<BugzillaAttribute, String> findDefaultValues(Element form) {
    Map<BugzillaAttribute, String> r = Collections15.hashMap();
    MultiMap<String, String> map = MultiMap.create();
    ExtractFormParameters.DEFAULT.formSelects(form, map);
    for (BugzillaAttribute bzAttr : BugzillaAttribute.ATTRIBUTES_WITH_DEFAULT_VALUES) {
      String selectName = BugzillaHTMLConstants.HTML_ATTRIBUTE_SELECTION_NAME_MAP.get(bzAttr);
      String value = map.getSingle(selectName);
      if (value != null) {
        r.put(bzAttr, value);
      }
    }
    return r;
  }

  private Element findPrivateDescriptionCheckbox(Element form) {
    Element input = JDOMUtils.searchElement(form, "input", "name", "commentprivacy");
    if (input == null)
      return null;
    String type = JDOMUtils.getAttributeValue(input, "type", null, false);
    return "checkbox".equalsIgnoreCase(type) ? input : null;
  }

  private List<BugzillaUser> findUserList(Element form) {
    final String[] userFields = {"assigned_to", "qa_contact", "cc"};
    for (String userField : userFields) {
      Element e = JDOMUtils.searchElement(form, "select", "name", userField);
      if (e != null) {
        List<BugzillaUser> r = OperUtils.extractShortUsersFromListOptions(e, myServerInfo.getEmailSuffix());
        return r;
      }
    }
    return null;
  }
}
