package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.http.HttpCancelledException;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.bugzilla.integration.data.BugzillaRDFConfig;
import com.almworks.bugzilla.integration.err.BugzillaLoginRequiredException;
import com.almworks.util.*;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.almworks.util.collections.Containers.collectList;
import static org.almworks.util.Collections15.arrayList;

public class LoadDictionaries extends BugzillaOperation {
  // if these lists are missing, then we probably not seeing the correct page
  private static final BugzillaAttribute[] REQUIRED_LISTS =
    { BugzillaAttribute.PRODUCT, BugzillaAttribute.STATUS };

  private final String myUrl;

  private final DependencyExtractor myRdfExtractor;
  private final DependencyExtractor[] myExtractors = new DependencyExtractor[] {
    new DependencyExtractor219(), new DependencyExtractor216(), new DependencyExtractor214() };

  private final boolean myAuthenticationAvailable;

  private final RunnableRE<BugzillaLists, ConnectorException> myOperation =
    new RunnableRE<BugzillaLists, ConnectorException>() {
      public BugzillaLists run() throws ConnectorException {
        return doRetrieve();
      }
    };

  private static final ConcurrentHashMap<String, Boolean> myWarnedAboutFormsByUrl = new ConcurrentHashMap<String, Boolean>();

  public LoadDictionaries(HttpMaterial material, String URL, boolean authenticationAvailable,
    AuthenticationMaster authMaster, BugzillaRDFConfig rdfConfig)
  {
    super(material, authMaster);
    assert URL != null;
    myUrl = URL;
    myAuthenticationAvailable = authenticationAvailable;
    myRdfExtractor = new DependencyExtractorRDF(rdfConfig);
  }

  public BugzillaLists retrieveLists() throws ConnectorException {
    return runOperation(myOperation);
  }

  private BugzillaLists doRetrieve() throws ConnectorException {
    LogHelper.debug("Load dictionaries STARTED");
    Document document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
    try {
      BugzillaErrorDetector.detectAndThrow(document, "loading value lists");
      if (myAuthenticationAvailable) {
        BugzillaErrorDetector.detectFailedAuthentication(document, "loading value lists");
      }
      BugzillaLists info = new BugzillaLists();
      try {
        loadLists(document, info);
      } catch (HttpCancelledException e) {
        throw new CancelledException(e);
      }
      checkSanity(info);
      loadDependencies(document, info);
      loadCustomFieldNames(document, info);
      info.fix();
      LogHelper.debug("Load dictionaries DONE");
      return info;
    } catch (ConnectorException e) {
      rethrowExceptionIfLoginIsRequired(e, document);
      throw e;
    }
  }

  private void loadCustomFieldNames(Document document, BugzillaLists info) {
    Element select = JDOMUtils.searchElement(document.getRootElement(), "select", "name", "field0-0-0");
    if (select == null)
      return;
    Iterator<Element> ii = JDOMUtils.searchElementIterator(select, "option");
    while (ii.hasNext()) {
      Element option = ii.next();
      String fieldId = JDOMUtils.getAttributeValue(option, "value", null, true);
      if (fieldId == null || !fieldId.startsWith("cf_"))
        continue;
      String displayableName = JDOMUtils.getTextTrim(option);
      info.addCustomFieldName(fieldId, displayableName);
    }
  }

  /**
   * Checks if the problem has happenned because we do not have access.
   */
  private void rethrowExceptionIfLoginIsRequired(ConnectorException e, Document document) throws ConnectorException {
    Element root = document.getRootElement();
    if (!BugzillaErrorDetector.isAskingForCredentials(root))
      return;
    throw new BugzillaLoginRequiredException(e.getLongDescription(), e);
  }

  private void checkSanity(BugzillaLists info) throws ConnectorException {
    for (BugzillaAttribute attribute : REQUIRED_LISTS) {
      if (info.isListMissing(attribute))
        throw new ConnectorException("query page is missing " + attribute,
          L.content("Cannot find attribute " + attribute.getName()), L.content(
          "Cannot load data from the query page - \nattribute " + attribute + " is missing.\n" +
            "This may be caused by heavy Bugzilla customization,\n" +
            "by accessing unsupported version of Bugzilla,\n" + "or by coming to a wrong page.\n\n" + "Details: " +
            myUrl));
    }
  }

  private void loadDependencies(Document document, BugzillaLists info) {
    info.setDependenciesPresent(true);

    if(myRdfExtractor.extractDependencies("", info)) {
      return;
    }

    Iterator<Element> ii = JDOMUtils.searchElementIterator(document.getRootElement(), "script");
    while (ii.hasNext()) {
      String script = loadScript(ii.next());
      if (!script.trim().isEmpty()) {
        for (DependencyExtractor extractor : myExtractors) {
          if (extractor.extractDependencies(script, info))
            return;
        }
      }
    }
    info.setDependenciesPresent(false);
    Log.warn("cannot extract dependencies information from " + myUrl);
  }

  private String loadScript(Element script) {
    // todo load script from external url
    StringBuffer result = new StringBuffer();
    List content = script.getContent();
    for (Object item : content) {
      if (item instanceof Text)
        result.append(((Text) item).getText());
      else if (item instanceof Comment)
        result.append(((Comment) item).getText());
      else if (item instanceof EntityRef)
        result.append('&').append(((EntityRef) item).getName()).append(';');
    }
    return result.toString();
  }

  private void loadLists(Document document, BugzillaLists info) throws HttpCancelledException {
    Element form = findStandardSearchForm(document);
    Set<String> keys = BugzillaHTMLConstants.HTML_SELECTION_NAME_ATTRIBUTE_MAP.keySet();
    for (String selectName : keys) {
      myMaterial.checkCancelled();
      BugzillaAttribute attribute = BugzillaHTMLConstants.HTML_SELECTION_NAME_ATTRIBUTE_MAP.get(selectName);
      List<String> list = loadSelectOptions(form, selectName, attribute);
      LogHelper.debug("Load dictionaries. Collected value for attribute:", attribute, list);
      if (list == null) {
        info.setListIsMissing(attribute);
      } else {
        info.getStringList(attribute).addAll(list);
      }
    }
  }

  /** Bugzilla may be customized to have additional &lt;form&gt;s with inputs that have the same id that we expect to see. We need to choose the standard one.
   * See also <a href="http://jira.almworks.com/browse/DZO-1022">DZO-1022</a> */
  @NotNull
  private Element findStandardSearchForm(Document document) {
    Element root = document.getRootElement();
    List<Element> forms = arrayList(1);
    List<Element> formCandidates = collectList(JDOMUtils.searchElementIterator(root, "form", "action", "buglist.cgi"));
    LogHelper.debug("Load dictionaries. Total search forms:", formCandidates.size());
    for (Element formCandidate: formCandidates) {
      if ("queryform".equals(JDOMUtils.getAttributeValue(formCandidate, "name", null, false))) {
        forms.add(formCandidate);
      }
    }
    LogHelper.debug("Load dictionaries. Forms found by name:", forms.size());
    if (forms.size() == 0) {
      forms = findFormsById(formCandidates);
      LogHelper.debug("Load dictionaries. Forms found by id:", forms.size());
    }
    Element form;
    int nForms = forms.size();
    if (nForms == 0) {
      int nCandidates = formCandidates.size();
      if (nCandidates > 0) {
        // Bugzilla before 2.16 does not specify name attribute in the <form> tag
        if (nCandidates > 1) warnAboutForms(formCandidates);
        LogHelper.debug("Load dictionaries. Choose a first form");
        form = formCandidates.get(0);
      } else {
        Log.error("Cannot find <form>, will search options in the entire " + myUrl);
        form = root;
      }
    } else if (nForms == 1) {
      LogHelper.debug("Load dictionaries. Choose the only form");
      form = forms.get(0);
    } else {
      // starting with bz400, the form we're interested in has attribute "id", let's give it a try
      List<Element> finalForms = findFormsById(forms);
      if (finalForms.size() == 1) {
        form = finalForms.get(0);
        LogHelper.debug("Load dictionaries. Choose the only form (by id)");
      } else {
        warnAboutForms(forms);
        LogHelper.debug("Load dictionaries. Choose a first form (by id)");
        form = forms.get(0);
      }
    }
    return form;
  }

  @NotNull
  private List<Element> findFormsById(List<Element> forms) {
    List<Element> candidates = arrayList(1);
    for (Element formCandidate: forms) {
      if ("queryform".equals(JDOMUtils.getAttributeValue(formCandidate, "id", null, false))) {
        candidates.add(formCandidate);
      }
    }
    return candidates;
  }

  private void warnAboutForms(List<Element> forms) {
    if (myWarnedAboutFormsByUrl.putIfAbsent(myUrl, true) == null) {
      StringBuilder msg = new StringBuilder("Several <form>s found, picking the first one ").append(myUrl);
      for (Element formCandidate : forms) {
        msg.append("\n").append(formCandidate.getAttributes());
      }
      Log.error(msg.toString());
    }
  }

  private List<String> loadSelectOptions(Element form, String selectName, BugzillaAttribute attribute) {
    Element select = JDOMUtils.searchElement(form, "SELECT", "NAME", selectName);
    if (select == null) {
      String msg = "cannot find selection[" + selectName + "] in " + myUrl;
      if (isBigDealAttribute(attribute)) {
        Log.warn(msg);
      } else if (!attribute.isUser()) {
        // User attributes are not selects since 3.0.0 at least, even if "usemenuforusers" is ON
        Log.debug(msg);
      }
      return null;
    }
    List<String> result = Collections15.arrayList();
    List<Element> options = JDOMUtils.searchElements(select, "OPTION", null, null);
    for (Element option : options) {
      String attributeValue = JDOMUtils.getAttributeValue(option, "VALUE", "", true);
      String textValue = JDOMUtils.getText(option);
      if (textValue.length() == 0 && attributeValue.length() == 0) {
        Log.warn("empty option in selection[" + selectName + "] in " + myUrl);
        continue;
      }
      if (attributeValue.length() > 0) {
        if (textValue.length() > 0 && !attributeValue.equalsIgnoreCase(textValue)) {
          if (!attributeValue.equalsIgnoreCase(textValue.trim())) {
            Log.warn(
              "different names in selection[" + selectName + "]: a[" + attributeValue + "] t[" + textValue + "]");
          }
        }
        result.add(attributeValue);
      } else {
        if ("ALL".equalsIgnoreCase(textValue)) {
          // this is a special case, only if attribute value is empty
          continue;
        }
        result.add(textValue);
      }
    }
    assert !result.contains("&nbsp;"):"&nbsp; value found for " + selectName;
    assert !result.contains(" "):"\" \" value found for " + selectName;
    assert !result.contains(""):"\"\" value found for " + selectName;
    return result;
  }

  private boolean isBigDealAttribute(BugzillaAttribute attribute) {
    return attribute != BugzillaAttribute.CLASSIFICATION && attribute != BugzillaAttribute.TARGET_MILESTONE && attribute != BugzillaAttribute.ASSIGNED_TO;
  }
}
