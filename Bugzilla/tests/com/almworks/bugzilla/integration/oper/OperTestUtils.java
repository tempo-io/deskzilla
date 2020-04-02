package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.http.HtmlUtils;
import org.jdom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URL;

public abstract class OperTestUtils {
  public static Document loadDocument(String resource) throws SAXException, IOException {
    URL url = OperTestUtils.class.getClassLoader().getResource(resource);
    Document document = HtmlUtils.buildHtmlDocument(new InputSource(new InputStreamReader((InputStream) url.getContent())));
    return document;
  }
}
