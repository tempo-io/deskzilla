package com.almworks.bugzilla.integration;

import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.err.BugzillaErrorException;
import com.almworks.bugzilla.integration.err.BugzillaLoginRequiredException;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.jdom.*;

import java.util.Iterator;
import java.util.List;

public class BugzillaErrorDetector {
  /**
   * Heuristic method that tries to detect bugzilla errors.
   */
  static BugzillaErrorException detect(String process, Element element, boolean searchTitle) {
    String errorTitle = null;
    String errorTitleUppercase = null;
    String errorDescription = null;
    boolean hasError = false;

    Element title = JDOMUtils.searchElement(element, "title");
    if (title != null) {
      errorTitle = extractText(title);
      errorTitleUppercase = Util.upper(errorTitle);
      if(searchTitle &&
        (errorTitleUppercase.contains("ERROR")
          || errorTitleUppercase.contains("DENIED")
          || errorTitleUppercase.contains("FAILED")
          || errorTitleUppercase.contains("ILLEGAL")))
      {
        hasError = true;
      }
      if("MATCH FAILED".equals(errorTitleUppercase)) {
        hasError = true;
      }
    }

    Iterator<Element> ii = JDOMUtils.searchElementIterator(element, "td");
    while (ii.hasNext()) {
      Element td = ii.next();
      if ("#ff0000".equalsIgnoreCase(JDOMUtils.getAttributeValue(td, "bgcolor", null, false))) {
        Element errorSpan = JDOMUtils.searchElement(td, "font", "size", "+2");
        if (errorSpan != null) {
          hasError = true;
          errorDescription = extractText(errorSpan);
          break;
        }
      }
      if ("error_msg".equalsIgnoreCase(JDOMUtils.getAttributeValue(td, "id", null, false))) {
        hasError = true;
        errorDescription = extractText(td);
        break;
      }
    }

    if (!hasError) {
      return null;
    }

    if (errorDescription == null && errorTitleUppercase != null && errorTitleUppercase.indexOf("MATCH FAILED") >= 0) {
      List<Element> cells = JDOMUtils.searchElements(element, "font", "color", "#ff0000");
      if (cells.size() > 0) {
        StringBuilder buffer = new StringBuilder();
        for (Element elem : cells) {
          Element tr = JDOMUtils.getAncestor(elem, "tr", 3);
          if (tr != null) {
            if (buffer.length() > 0)
              buffer.append(", ");
            buffer.append(JDOMUtils.getTextTrim(tr));
          }
        }
        if (buffer.length() > 0)
          errorDescription = buffer.toString();
      }
    }

    if (errorTitle == null && errorDescription == null) {
      errorTitle = errorDescription = "Bugzilla Error";
    } else {
      if (errorTitle == null)
        errorTitle = errorDescription;
      if (errorDescription == null)
        errorDescription = errorTitle;
    }

    if ("Internal Error".equalsIgnoreCase(errorTitle)) {
      errorTitle = "Bugzilla Error";
    }

    return BugzillaErrorException.create(process, errorTitle, StringUtil.normalizeWhitespace(errorDescription));
  }

  private static String extractText(Element errorSpan) {
    List<Content> content = errorSpan.getContent();
    StringBuffer result = new StringBuffer();
    for (Content c : content) {
      String toAppend = null;

      if (c instanceof Text) {
        toAppend = ((Text) c).getTextNormalize();
      } else if (c instanceof Element) {
        Element elem = (Element) c;
        toAppend = extractText(elem);
        if ("A".equalsIgnoreCase(elem.getName())) {
          toAppend = "[" + toAppend + "]";
        }
      }

      if (toAppend != null && toAppend.length() > 0) {
        int length = result.length();
        char firstChar = toAppend.charAt(0);
        if (length > 0 && !Character.isWhitespace(result.charAt(length - 1)) && !Character.isWhitespace(firstChar) &&
          firstChar != '.' && firstChar != ',' && firstChar != ';')
        {
          result.append(' ');
        }
        result.append(toAppend);
      }
    }
    return result.toString();
  }

  public static void detectAndThrow(Document document, String process) throws ConnectorException {
    detectAndThrow(document, process, false);
  }

  public static void detectAndThrow(Document document, String process, boolean acceptLoginPrompt)
    throws ConnectorException
  {
    detectAndThrow(document, process, acceptLoginPrompt, true);
  }

  public static void detectAndThrow(Document document, String process, boolean acceptLoginPrompt, boolean searchInTitle)
    throws ConnectorException
  {
    if (!acceptLoginPrompt)
      detectLoginPrompt(document);
    BugzillaErrorException error = detect(process, document.getRootElement(), searchInTitle);
    if (error != null) {
      throw error;
    }
  }

  private static void detectLoginPrompt(Document document) throws BugzillaLoginRequiredException {
    if (isAskingForCredentials(document.getRootElement()))
      throw new BugzillaLoginRequiredException();
  }

  public static boolean isAskingForCredentials(Element root) {
    boolean bzlogin = isAskingFor(root, "Bugzilla_login", "Bugzilla_password");
    boolean ldaplogin = isAskingFor(root, "LDAP_login", "LDAP_password");
    return bzlogin || ldaplogin;
  }

  private static boolean isAskingFor(Element root, String loginInput, String passwordInput) {
    Element login = getVisibleInputFieldNotInTheLinksArea(root, loginInput);
    Element password = getVisibleInputFieldNotInTheLinksArea(root, passwordInput);
    return login != null && password != null;
  }

  public static Element getVisibleInputFieldNotInTheLinksArea(Element root, String name) {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(root, "input", "name", name);
    while(ii.hasNext()) {
      Element element = ii.next();
      String value = JDOMUtils.getAttributeValue(element, "type", "text", false);
      if ("hidden".equalsIgnoreCase(value))
        continue;
      // skip the mini-logins on top and bottom - added with bz 3.3
      Element form = JDOMUtils.getAncestor(element, "form");
      if (form != null) {
        String fclass = Util.lower(JDOMUtils.getAttributeValue(form, "class", "", false));
        String fid = Util.lower(JDOMUtils.getAttributeValue(form, "id", "", false));
        if (fclass.indexOf("mini_login") >= 0 || fid.indexOf("mini_login") >= 0) {
          continue;
        }
      }
      return element;
    }
    return null;
  }

  public static void detectFailedAuthentication(Document document, String process)
    throws BugzillaLoginRequiredException
  {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(document.getRootElement(), "a");
    boolean logoutFound = false;
    boolean loginFound = false;
    while (ii.hasNext()) {
      Element a = ii.next();
      String  href = JDOMUtils.getAttributeValue(a, "href", null, true);
      if (href == null)
        continue;
      if (BugzillaHTMLConstants.URL_RELOGIN.equalsIgnoreCase(href) || BugzillaHTMLConstants.URL_RELOGIN_2.equalsIgnoreCase(href)) {
        logoutFound = true;
      } else if (BugzillaHTMLConstants.URL_LOGIN_SCREEN.equalsIgnoreCase(href) ||
        BugzillaHTMLConstants.URL_INDEX_LOGIN_SCREEN.equalsIgnoreCase(href))
      {
        loginFound = true;
      } else {
        String name = JDOMUtils.getTextTrim(a);
        if ("log in".equalsIgnoreCase(name) || "login".equalsIgnoreCase(name)) {
          loginFound = true;
        }
      }
    }
    if (logoutFound) {
      // means we're authenticated
      return;
    }
    if (loginFound) {
      throw new BugzillaLoginRequiredException();
    }

    Log.warn("cannot detect if authentication was successful");
    // return as if ok
  }

  public static boolean isExtensionDisabled(BugzillaErrorException e) {
    return "Extension Disabled".equalsIgnoreCase(e.getErrorTitle());
  }

  /**
   * This method tries to find any error message in provided document. Use this method if failed detect error other way.
   * @throws BugzillaErrorException if any error message has been detected
   */
  public static void throwAnyError(Document document, String process) throws BugzillaErrorException {
    Iterator<Element> it =
      JDOMUtils.searchElementIterator(document.getRootElement(), null, "id", "error_msg");
    while (it.hasNext()) {
      Element element = it.next();
      String error = JDOMUtils.getText(element).trim();
      if (!error.isEmpty())
        throw BugzillaErrorException.create(process, "Bugzilla Error", StringUtil.normalizeWhitespace(error));
    }
  }
}
