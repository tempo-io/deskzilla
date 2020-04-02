package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.*;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.err.BugzillaLoginRequiredException;
import com.almworks.bugzilla.integration.err.BugzillaResponseException;
import com.almworks.platform.DiagnosticRecorder;
import com.almworks.util.RunnableRE;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class BugzillaOperation {
  @Nullable
  protected final AuthenticationMaster myAuthMaster;
  protected final HttpMaterial myMaterial;
  private List<DocumentLoader> myLoaders = null;

  public BugzillaOperation(HttpMaterial material, @Nullable AuthenticationMaster authenticationMaster) {
    myAuthMaster = authenticationMaster;
    myMaterial = material;
  }

  public BugzillaOperation(ServerInfo serverInfo) {
    this(serverInfo.getMaterial(), serverInfo.getMaster());
  }

  protected synchronized DocumentLoader getDocumentLoader(String url, boolean allowRetries, String scriptOverride) {
    DocumentLoader loader = getDocumentLoader(url, allowRetries);
    loader.setScriptOverride(scriptOverride);
    return loader;
  }

  protected synchronized DocumentLoader getDocumentLoader(String url, boolean allowRetries) {
    DocumentLoader loader = new DocumentLoader(myMaterial, url, getDumpSpecs());
    if (!allowRetries)
      loader.noRetries();
    if (myLoaders == null)
      myLoaders = Collections15.arrayList();
    myLoaders.add(loader);
    return loader;
  }

  private List<HttpDumper.DumpSpec> getDumpSpecs() {
    final DiagnosticRecorder recorder = BugzillaEnv.getDiagnosticRecorder();
    final File diagDir = recorder != null ? recorder.getSessionDir() : null;
    final HttpDumper.DumpSpec diagSpec = diagDir != null
      ? new HttpDumper.DumpSpec(HttpDumper.DumpLevel.ALL, diagDir) : null;

    final HttpDumper.DumpLevel envLevel = BugzillaEnv.getDumpLevel();
    final HttpDumper.DumpSpec envSpec = envLevel != HttpDumper.DumpLevel.NONE
      ? new HttpDumper.DumpSpec(envLevel, BugzillaEnv.getLogDir()) : null;

    return HttpDumper.DumpSpec.listOfTwo(diagSpec, envSpec);
  }

  protected <R> R runOperation(RunnableRE<R, ConnectorException> operation) throws ConnectorException {
    myLoaders = null; // todo really clear?
    ConnectorException throwingException = null;
    try {
      R result = null;
      try {
        result = operation.run();
      } catch (BugzillaLoginRequiredException e) {
        // if bugzilla asks for credentials then we have to clear cookies that did not satisfy bugzilla
        if (myAuthMaster != null) {
          myAuthMaster.reauthenticate();
          result = operation.run();
        }
      }
      return result;
    } catch (Exception e) {
      if (e instanceof BugzillaLoginRequiredException) {
        if (myAuthMaster != null) {
          myAuthMaster.clearAuthentication();
        }
      }
      if (e instanceof ConnectorException) {
        ConnectorException ie = (ConnectorException) e;
        throwingException = ie;
        throw ie;
      } else {
        if (e instanceof RuntimeException)
          throw (RuntimeException) e;
        else
          throw new Failure(e);
      }
    } finally {
      try {
        if (myLoaders != null) {
          int length = myLoaders.size();
          for (int i = 0; i < length; i++) {
            DocumentLoader loader = myLoaders.get(i);
            if (i + 1 == length) {
              loader.setSuccess(throwingException == null);
              loader.setException(throwingException);
            } else {
              loader.setSuccess(true);
            }
            loader.finish();
          }
          myLoaders.clear();
          myLoaders = null;
        }
      } catch (Exception e) {
        // ignore
        Log.error(e);
      }
    }
  }

  protected Element loadSubmitForm(final String baseUrl, final String product) throws ConnectorException {
    return runOperation(new RunnableRE<Element, ConnectorException>() {
      @Override
      public Element run() throws ConnectorException {
        Element page = loadSubmitPage(baseUrl, product);
        return OperUtils.findSubmitFormElement(page, false);
      }
    });
  }

  protected Element loadSubmitPage(String baseUrl, String product) throws ConnectorException {
    Element page;
    try {
      String url = baseUrl + BugzillaHTMLConstants.URL_ENTER_BUG + URLEncoder.encode(product, "UTF-8");
      DocumentLoader loader = getDocumentLoader(url, true);
      Document document = loader.httpGET().loadHTML();
      BugzillaErrorDetector.detectAndThrow(document, "loading defaults for product " + product);
      page = document.getRootElement();
    } catch (UnsupportedEncodingException e) {
      Log.error(e);
      throw new ConnectorException("internal error", e, "Internal Error", "Internal Error");
    }
    return page;
  }

  /**
   * Wraps DocumentLoader.loadXML to detect problems reported in HTML format. If not wrapped, any user-level error
   * (like no permissions) would result in Deskzilla reporting problems with XML parsing.
   * <p>
   * Any operation that loads XML should use this method instead of plain loadXML()
   * <p>
   * todo make DocumentLoader an interface and decorate
   */
  protected Document loadXMLSafe(DocumentLoader loader, String url) throws ConnectorException {
    Document document = null;
    try {
      document = loader.loadXML();
    } catch (CannotParseException e) {
      Document html = null;
      try {
        html = loader.loadHTML();
      } catch (ConnectorException ee) {
        Log.warn("cannot parse XML, cannot parse HTML", ee);
        throw e;
      }
      // look for errors
      BugzillaErrorDetector.detectAndThrow(html, "loading xml");
      // otherwise just throw title
      throw createGenericCannotParseException(url, html, e);
    }
    return document;
  }

  private ConnectorException createGenericCannotParseException(String url, Document html, CannotParseException e) {
    Element title = JDOMUtils.searchElement(html.getRootElement(), "title");
    String s = title == null ? "" : JDOMUtils.getTextTrim(title);

    String message = s.length() == 0 ? "error parsing XML" : "error parsing XML: server returned [" + s + "]";
    String shortName = s.length() == 0 ? "cannot understand Bugzilla" : "Bugzilla says: " + s;

    String longMessage = "There seems to be a problem understanding a remote server web page.\n" +
      "This may be a compatibility issue, please verify that your server's version is supported.\n" + "\n" +
      "Details:\n" + url + "\n" + message;

    return new BugzillaResponseException(message, e, shortName, longMessage);
  }
}
