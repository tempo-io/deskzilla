package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.CannotParseException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.HttpMaterial;
import com.almworks.api.http.HttpUtils;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.util.*;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;

public class RDFConfigLoader extends BugzillaOperation {
  private final String myBaseURL;
  private final List<String> myProductsList;

  public RDFConfigLoader(HttpMaterial material, String baseURL, List<String> productsList, AuthenticationMaster authMaster) {
    super(material, authMaster);
    myBaseURL = baseURL;
    myProductsList = productsList;
  }

  public BugzillaRDFConfig loadRDFConfig() throws ConnectorException {
    return runOperation(new RunnableRE<BugzillaRDFConfig, ConnectorException>() {
      public BugzillaRDFConfig run() throws ConnectorException {
        return doLoad();
      }
    });
  }

  private BugzillaRDFConfig doLoad() throws ConnectorException {
    String url = myBaseURL + "config.cgi?ctype=rdf";
    if (myProductsList != null) {
      if (myProductsList.isEmpty()) {
        url += "&product=nonexisting";
      } else {
        for (String product : myProductsList) {
          if (product != null) {
            url += "&product=" + HttpUtils.encode(product);
          }
        }
      }
    }
    DocumentLoader loader = getDocumentLoader(url, true);
    loader.httpGET();
    Document xml = loadXMLSafe(loader, url);
    LogHelper.debug("RDFConfig: loaded");

    BugzillaRDFConfig r = new BugzillaRDFConfig();

    // we're forgetting about namespaces, as the current naming is sufficient
    // we also getting only things we need for now

    Element rdf = xml.getRootElement();
    if (!"rdf".equalsIgnoreCase(rdf.getName()))
      throw new CannotParseException(url, "bad RDF [" + rdf + "]");

    Element installation = JDOMUtils.getChild(rdf, "installation");
    if (installation == null)
      throw new CannotParseException(url, "no <installation>");

    String installVersion = JDOMUtils.getChildText(installation, "install_version");
    LogHelper.debug("RDFConfig: install_version:", installVersion);
    if (!installVersion.isEmpty()) {
      r.setInstallVersion(installVersion);
    }

    loadCustomFields(installation, url, r);
    loadStatusInfos(installation, r, installVersion);
    loadDependencies(installation, r);
    loadKeywords(installation, r);

    LogHelper.debug("RDFConfig DOME");
    return r;
  }

  private void loadKeywords(Element installation, BugzillaRDFConfig r) {
    Element keywords = JDOMUtils.getChild(installation, "keywords");
    if (keywords == null) {
      LogHelper.debug("No KEYWORDs in RDF config");
      return;
    }
    Iterator<Element> ii = JDOMUtils.searchElementIterator(keywords, "keyword");
    while (ii.hasNext()) {
      Element keyword = ii.next();
      String name = JDOMUtils.getChildText(keyword, "name");
      String description = JDOMUtils.getChildText(keyword, "description");
      r.addKeywords(name, description);
    }
  }

  private static void loadCustomFields(Element installation, String url, BugzillaRDFConfig r) throws CannotParseException {
    Element fields = JDOMUtils.getChild(installation, "fields");
    if (fields == null)
      throw new CannotParseException(url, "no <fields>");

    Iterator<Element> ii = JDOMUtils.searchElementIterator(fields, "field");
    int order = 1;
    while (ii.hasNext()) {
      Element field = ii.next();
      String id = JDOMUtils.getChildText(field, "name");
      if (!id.startsWith("cf_")) {
        // not a custom field
        continue;
      }
      String name = JDOMUtils.getChildText(field, "description");
      int type = Util.toInt(JDOMUtils.getChildText(field, "type"), -1);
      boolean onNewBugs = "1".equals(JDOMUtils.getChildText(field, "enter_bug"));

      if (name.trim().length() == 0 || type <= 0)
        continue;

      CustomFieldType ftype;
      switch (type) {
      case 1:
        ftype = CustomFieldType.TEXT;
        break;
      case 2:
        ftype = CustomFieldType.CHOICE;
        break;
      case 3:
        ftype = CustomFieldType.MULTI_SELECT;
        break;
      case 4:
        ftype = CustomFieldType.LARGE_TEXT;
        break;
      case 5:
        ftype = CustomFieldType.DATE_TIME;
        break;
      case 6:
        ftype = CustomFieldType.BUG_ID;
        break;
      default:
        ftype = CustomFieldType.UNKNOWN;
        break;
      }

      if (ftype == CustomFieldType.UNKNOWN) {
        continue;
      }

      List<String> valuesList = null;
      if (ftype.isEnumerable()) {
        Element fieldOptions = JDOMUtils.getChild(installation, id);
        if (fieldOptions != null) {
          valuesList = Collections15.arrayList();
          Iterator<Element> jj = JDOMUtils.searchElementIterator(fieldOptions, "li");
          while (jj.hasNext())
            valuesList.add(JDOMUtils.getText(jj.next()));
        }
      }

      CustomFieldInfo cfi = new CustomFieldInfo(id, name, ftype, order++, onNewBugs, valuesList);
      r.addCustomField(cfi);
    }
  }

  private static void loadStatusInfos(Element installation, BugzillaRDFConfig config, String installVersion) {
    List<String> unknownStatuses = doLoadStatus(installation, installVersion, "status");
    List<String> openStatuses = doLoadStatus(installation, installVersion, "status_open");
    List<String> closedStatuses = doLoadStatus(installation, installVersion, "status_closed");
    unknownStatuses.removeAll(openStatuses);
    unknownStatuses.removeAll(closedStatuses);
    if (!unknownStatuses.isEmpty()) {
      Log.warn("RDFCL: Not all statuses listed in Open and Closed sections (" + installVersion + ')');
      for (String unknown : unknownStatuses) {
        config.addStatusInfo(unknown, null);
      }
    }
    for (String open : openStatuses) {
      config.addStatusInfo(open, true);
    }
    for (String closed : closedStatuses) {
      config.addStatusInfo(closed, false);
    }
  }

  private static List<String> doLoadStatus(Element installation, String installVersion, String tag) {
    Element statuses = JDOMUtils.getChild(installation, tag);
    if (statuses == null) {
      Log.warn("RDFCL: cannot load " + tag + " (" + installVersion + ')');
      return Collections.emptyList();
    } else {
      Element seq = JDOMUtils.getChild(statuses, "Seq");
      if (seq == null) {
        assert false;
        return Collections.emptyList();
      }
      return JDOMUtils.GET_TEXT_TRIM.collectList(seq.getChildren());
    }
  }

  private static void loadDependencies(Element installation, BugzillaRDFConfig r) {
    LogHelper.debug("RDFConfig: processing dependencies");
    if(Env.getBoolean("no.rdf.dependencies", false)) {
      Log.debug("RDFCL: RDF dependency extraction is disabled");
      return;
    }

    final Element products = JDOMUtils.searchElement(installation, "products");
    if(products == null) {
      Log.warn("RDFCL: no <products> in <installation>");
      return;
    }

    final List<String> prodNames = Collections15.arrayList();
    final MultiMap<String, String> components = MultiMap.create();
    final MultiMap<String, String> versions = MultiMap.create();
    final MultiMap<String, String> milestones = MultiMap.create();

    try {
      for(final Element product : JDOMUtils.searchElements(products, "product")) {
        loadDependencies(product, prodNames, components, versions, milestones);
      }
    } catch(Break b) {
      LogHelper.warning("RDFConfig: processing dependencies: break:", b.getMessage());
      return;
    }

    r.setProductDependencies(prodNames, components, versions, milestones);
    LogHelper.debug("RDFConfig: processing dependencies DONE");
  }

  private static void loadDependencies(
    Element product, List<String> prodNames, MultiMap<String, String> components,
    MultiMap<String, String> versions, MultiMap<String, String> milestones) throws Break
  {
    final Element nameElement = JDOMUtils.searchElement(product, "name");
    Break.breakIfNull(nameElement, "RDFCL: no <name> in <product>");

    final String name = JDOMUtils.getTextTrim(nameElement);
    Break.breakIf(name.isEmpty(), "RDFCL: <product>'s <name> is empty");

    prodNames.add(name);
    LogHelper.debug("RDFConfig: processing dependencies: product:", name);
    loadDependency(product, name, "components", components, true);
    loadDependency(product, name, "versions", versions, true);
    loadDependency(product, name, "target_milestones", milestones, false);
    LogHelper.debug("RDFConfig: processing dependencies: product:", name, "DONE");
  }

  private static void loadDependency(
    Element product, String prodName, String tagName, MultiMap<String, String> result, boolean breakIfAbsent)
    throws Break
  {
    LogHelper.debug("RDFConfig: processing dependencies: product:", prodName, "dependency:", tagName);
    final Element el = JDOMUtils.searchElement(product, tagName);
    if(el == null) {
      if(breakIfAbsent) {
        Break.breakHere("RDFCL: no <%s> in <product> %s", tagName, prodName);
      }
      return;
    }

    for (Element li : JDOMUtils.searchElements(el, "li")) {
      String resource = JDOMUtils.getAttributeValue(li, "resource", "", true);
      Break.breakIf(resource.isEmpty(), "RDFCL: <%s> resource is empty in <product> %s", tagName, prodName);
      result.add(prodName, extractName(resource));
    }
    LogHelper.debug("RDFConfig: processing dependencies: product:", prodName, "dependency:", tagName, "DONE:", result.getAll(prodName));
  }

  private static String extractName(@NotNull String resource) throws Break {
    int queryStart = resource.indexOf('?');
    if (queryStart < 0 || queryStart == resource.length() - 1) Break.breakHere("RDFCL: no query in URL '%s'", resource);
    String query = resource.substring(queryStart + 1);
    for (String fragment : query.split("&")) {
      fragment = StringUtil.urlDecode(fragment);
      if (fragment.startsWith("name=") && fragment.length() > 5) {
        return fragment.substring(5);
      }
    }
    return Break.breakHere("RDFCL: cannot extract name from %s", resource);
  }
}
