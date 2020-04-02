package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.*;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Document;
import org.jdom.Element;

import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public class LoadSavedSearches extends BugzillaOperation {
  private final String myUrl;

  public LoadSavedSearches(HttpMaterial material, String url, AuthenticationMaster authMaster) {
    super(material, authMaster);
    assert url != null;
    myUrl = url;
  }

  public List<Pair<String, String>> loadSavedSearches() throws ConnectorException {
    return runOperation(new RunnableRE<List<Pair<String, String>>, ConnectorException>() {
      public List<Pair<String, String>> run() throws ConnectorException {
        try {
          List<Pair<String, String>> list = Collections15.arrayList();
          DocumentLoader loader = getDocumentLoader(myUrl + BugzillaHTMLConstants.URL_QUERY_PAGE_NOFORMAT, true);
          Document document = loader.httpGET().loadHTML();
          Element divLinksSaved = JDOMUtils.searchElement(document.getRootElement(), "div", "id", "links-saved");
          List<Element> links = null;
          if (divLinksSaved != null) {
            Element divLinks = JDOMUtils.searchElement(divLinksSaved, "div", "class", "links");
            if (divLinks != null)
              links = JDOMUtils.searchElements(divLinks, "a");
          }

          if (links == null)
            links = JDOMUtils.searchElements(document.getRootElement(), "a");

          for (int i = 0; i < links.size(); i++) {
            Element a = links.get(i);
            String href = JDOMUtils.getAttributeValue(a, "href", "", true);
            if (href != null &&
              (href.startsWith(myUrl + BugzillaHTMLConstants.URL_BUGLIST_PREFIX) ||
              href.startsWith(BugzillaHTMLConstants.URL_BUGLIST_PREFIX))) {

              list.add(Pair.create(JDOMUtils.getTextTrim(a), href));
            }
          }

          return list;
        } catch (CannotParseException e) {
          Log.debug(e);
          throw e;
        } catch (ConnectionException e) {
          Log.debug(e);
          throw e;
        }
      }
    });
  }
}
