package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.HtmlUtils;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.BugzillaErrorDetector;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.util.RunnableRE;
import com.almworks.util.Terms;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.i18n.Local;
import com.almworks.util.xml.JDOMUtils;
import org.jdom.Document;
import org.jdom.Element;

public class LoadCreateAttachmentPage extends BugzillaOperation {
  private final String myUrl;

  public LoadCreateAttachmentPage(HttpMaterial material, String baseURL, String bugId,
    AuthenticationMaster authMaster)
  {
    super(material, authMaster);
    assert bugId != null;
    myUrl = baseURL + BugzillaHTMLConstants.URL_CREATE_ATTACHMENT.replaceAll("\\$id\\$", bugId); // DZO-675
  }

  public MultiMap<String, String> loadDefaultFormParameters() throws ConnectorException {
    return runOperation(new RunnableRE<MultiMap<String, String>, ConnectorException>() {
      public MultiMap<String, String> run() throws ConnectorException {
        Document document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "loading create attachment page");
        Element root = document.getRootElement();
        Element element = JDOMUtils.searchElement(root, "form", "name", "entryform");
        if (element == null) {
          throw new ConnectorException("cannot load create attachment page", "Cannot create attachment",
            "Cannot create attachment.\n\n" + Local.text(Terms.key_Deskzilla) +
              " was not able to create attachment because\n" + "attachment page cannot be loaded.\n\n" +
              "Please check if you can reach this page with your browser:\n" + myUrl);
        }
        return HtmlUtils.extractDefaultFormParameters(element);
      }
    });
  }
}
