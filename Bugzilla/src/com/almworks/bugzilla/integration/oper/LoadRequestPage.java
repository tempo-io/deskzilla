package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.HttpFailureConnectionException;
import com.almworks.api.http.HttpMaterial;
import com.almworks.api.http.HttpUtils;
import com.almworks.bugzilla.integration.BugzillaErrorDetector;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.collections.Containers;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.Iterator;
import java.util.List;

public class LoadRequestPage extends BugzillaOperation {
  private final String myUrl;
  private static final Pair<List<String>, List<Integer>> EMPTY_PAIR =
    Pair.create(Collections15.<String>emptyList(), Collections15.<Integer>emptyList());
  private static final Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>> EMPTY_RESULT =
    Pair.create(EMPTY_PAIR, EMPTY_PAIR);

  /**
   * @param singleProduct if connection is configured only for single product this operation can load flags only for the product
   */
  public LoadRequestPage(HttpMaterial material, @Nullable AuthenticationMaster authenticationMaster, String baseUrl,
    @Nullable String singleProduct)
  {
    super(material, authenticationMaster);
    StringBuilder url = new StringBuilder(baseUrl + "request.cgi?action=queue&type=all&component=&group=type");
    HttpUtils.addGetParameter(url, "product", singleProduct != null ? singleProduct : "");
    myUrl = url.toString();
  }

  /**
   * Loads all flag names and all bug/attachment ids to get full info about all flags. Flags that has no usages on server
   * arent collected.
   *
   * @return &lt;bugs, attachments&gt;<br>
   *         where bugs and attachments are pair<br>
   *         &lt;flagNames, ids&gt;<br>
   *         where flagNames is names of all known flags and ids is sufficient set of IDs to load to get info about all flags<br>
   *         See description of <a href="http://jira.almworks.com/browse/DZO-665">DZO-665</a><br>
   *         Returns null if page has unsupported format and cannot be loaded. On successful load returns not null and
   *         all parts are not null too.
   */
  @Nullable
  public Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>> loadRequestPage()
    throws ConnectorException
  {
    return runOperation(new MyLoader());
  }

  private static Pair<List<String>, List<Integer>> createResult(List<String> flags, List<Integer> ids) {
    return Pair.create(Containers.toUniqueSortedList(flags), Containers.toUniqueSortedList(ids));
  }

  private static void skipUpToFlagData(Iterator<Element> it) throws ConnectorException {
    while (it.hasNext()) {
      Element element = it.next();
      if ("form".equalsIgnoreCase(element.getName()))
        return;
    }
    throw unexpectedHtml("Cannot find <form>. Required to locate flags");
  }

  public static String tailAfter(String text, String substring) {
    if (text == null)
      return null;
    int index = text.indexOf(substring);
    if (index < 0)
      return null;
    return text.substring(index + substring.length());
  }

  private static ConnectorException unexpectedHtml(String longDescription) {
    return new ConnectorException("Cannot load requests.cgi", "Unexpected page HTML", longDescription);
  }

  private class MyLoader implements
    RunnableRE<Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>>, ConnectorException>
  {
    @Override
    public Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>> run() throws ConnectorException {
      Document document = null;
      try {
        document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
      } catch (HttpFailureConnectionException e) {
        if (e.getStatusCode() != 404)
          Log.warn("Cannot load requests.cgi", e);
        return EMPTY_RESULT;
      }
      return new RequestCGIParser(document).parse();
    }
  }


  static class RequestCGIParser {
    private final Document myDocument;
    private final List<Integer> myBugs = Collections15.arrayList();
    private final List<Integer> myAttachments = Collections15.arrayList();
    private final List<String> myBugFlags = Collections15.arrayList();
    private final List<String> myAttachFlags = Collections15.arrayList();

    public RequestCGIParser(Document document) {
      myDocument = document;
    }

    public Pair<Pair<List<String>, List<Integer>>, Pair<List<String>, List<Integer>>> parse()
      throws ConnectorException
    {
      Element div = JDOMUtils.searchElement(myDocument.getRootElement(), "div", "id", "bugzilla-body");
      if (div == null) {
        Element form = JDOMUtils.searchElement(myDocument.getRootElement(), "form", "action", "request.cgi");
        if (form == null) {
          BugzillaErrorDetector.detectAndThrow(myDocument, "loading request page");
          throw unexpectedHtml(
            "Cannot find <div id='bugzilla-body'> or <form action='request.cgi'>. Required to load all flags used on server");
        }
        div = form.getParentElement();
      }
      List<Element> children = div.getChildren();
      Iterator<Element> it = children.iterator();
      skipUpToFlagData(it);
      while (it.hasNext()) {
        Element hdr = it.next();
        String hdrText = hdr.getText();
        if ("p".equalsIgnoreCase(hdr.getName())) {
          String text = hdrText;
          if (text == null || !text.contains("No requests."))
            Log.warn("Flags: Unexpected request result " + hdrText);
          return EMPTY_RESULT;
        }
        String flagName = tailAfter(hdrText, "Flag:");
        if (flagName == null)
          continue;
        if (!it.hasNext()) {
          Log.warn("Customized request.cgi. Unexpected end of flags list. Last element: " + hdr);
          break;
        }
        Element table = it.next();
        flagName = flagName.trim();
        processFlagTable(table, flagName.trim());
      }
      return Pair.create(createResult(myBugFlags, myBugs), createResult(myAttachFlags, myAttachments));
    }

    private void processFlagTable(Element table, String flagName) {
      Iterator<Element> trs = JDOMUtils.searchElementIterator(table, "tr");
      while (trs.hasNext()) {
        Element tr = trs.next();
        Iterator<Element> as = JDOMUtils.searchElementIterator(tr, "a");
        Integer bugId = null;
        Integer attachmentId = null;
        while (as.hasNext()) {
          Element a = as.next();
          String href = JDOMUtils.getAttributeValue(a, "href", null, false);
          if (href == null)
            continue;
          String bugIdStr = tailAfter(href, "show_bug.cgi?id=");
          try {
            if (bugIdStr != null) {
              bugId = Integer.parseInt(bugIdStr);
            } else {
              String strAttachment = tailAfter(href, "attachment.cgi?id=");
              if (strAttachment != null) {
                int index = 0;
                while (index < strAttachment.length() && Character.isDigit(strAttachment.charAt(index)))
                  index++;
                attachmentId = Integer.parseInt(strAttachment.substring(0, index));
              }
            }
          } catch (NumberFormatException e) {
            continue;
          }
        }
        if (attachmentId != null) {
//          myAttachments.add(attachmentId); // DZO-737 remove attachment flags
//          myAttachFlags.add(flagName);
          return;
        }
        if (bugId != null) {
          myBugs.add(bugId);
          myBugFlags.add(flagName);
          return;
        }
      }
    }
  }
}
