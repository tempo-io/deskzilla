package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.http.HttpMaterial;
import com.almworks.util.RunnableRE;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.jdom.Document;
import org.jdom.Element;

import java.util.List;
import java.util.Map;

public class LoadKeywords extends BugzillaOperation {
  private String myUrl;

  public LoadKeywords(HttpMaterial material, String url, AuthenticationMaster authMaster) {
    super(material, authMaster);
    myUrl = url;
  }

  public Map<String, String> loadKeywords() throws ConnectorException {
    return runOperation(new RunnableRE<Map<String, String>, ConnectorException>() {
      public Map<String, String> run() throws ConnectorException {
        return load();
      }
    });
  }

  private Map<String, String> load() throws ConnectorException {
    Document document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
    Element body = JDOMUtils.searchElement(document.getRootElement(), "div", "id", "bugzilla-body");
    if (body != null) {
      Map<String, String> result = Collections15.hashMap();
      List<Element> rows = JDOMUtils.searchElements(document.getRootElement(), "tr");
      for (int i = 1; i < rows.size(); i++) {
        Element element = rows.get(i);
        List<Element> children = element.getChildren();
        if (children.size() < 2) continue;
        Element first = children.get(0);
        Element name = JDOMUtils.searchElement(first, "a");
        if (name == null && isBugzilla40Keyword(element, first)) {
          name = first;
        }
        if (name != null) {
          String strName = JDOMUtils.getTextTrim(name);
          String strDescr = JDOMUtils.getTextTrim(children.get(1));
          result.put(strName, strDescr);
        }
      }
      return result;
    }
    return null;
  }

  private boolean isBugzilla40Keyword(Element tr, Element child) {
    return "th".equalsIgnoreCase(child.getName())
      && JDOMUtils.getTextTrim(child).equals(JDOMUtils.getAttributeValue(tr, "id", null, true));
  }
}
