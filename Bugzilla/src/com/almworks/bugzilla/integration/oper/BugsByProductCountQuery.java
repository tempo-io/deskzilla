package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.util.RunnableRE;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * An operation to load bug counts for each project.
 */
public class BugsByProductCountQuery extends BugzillaOperation
  implements RunnableRE<Map<String, Integer>, ConnectorException>
{
  private final String myQueryUrl;

  public BugsByProductCountQuery(
    HttpMaterial material, String baseUrl, @Nullable AuthenticationMaster authenticationMaster)
  {
    super(material, authenticationMaster);
    myQueryUrl = baseUrl + BugzillaHTMLConstants.URL_BUG_COUNT_BY_PRODUCTS;
  }

  public Map<String, Integer> countBugs() throws ConnectorException {
    return runOperation(this);
  }

  @Override
  public Map<String, Integer> run() throws ConnectorException {
    final DocumentLoader loader = prepareLoader();
    final String content = loadContent(loader);
    return parseContent(content);
  }

  private DocumentLoader prepareLoader() {
    final DocumentLoader loader = getDocumentLoader(myQueryUrl, true);
    loader.addRedirectUriHandler(LoadQuery.CTYPE_REDIRECT_VERIFIER);
    return loader;
  }

  private String loadContent(DocumentLoader loader) throws ConnectorException {
    return loader.httpGET().loadString();
  }

  private Map<String, Integer> parseContent(String content) {
    final String[] lines = content.split("\\n");
    return parseLines(lines);
  }

  private Map<String, Integer> parseLines(String[] lines) {
    final Map<String, Integer> result = Collections15.hashMap();
    for(final String line : lines) {
      parseLine(line, result);
    }
    return result;
  }

  private void parseLine(String line, Map<String, Integer> result) {
    final int commaIndex = line.lastIndexOf(',');
    if(commaIndex > 0 && commaIndex < line.length() - 1) {
      final String productPart = line.substring(0, commaIndex).trim();
      final String countPart = line.substring(commaIndex + 1).trim();
      parseParts(productPart, countPart, result);
    }
  }

  private void parseParts(String productPart, String countPart, Map<String, Integer> result) {
    final int count = parseCount(countPart);
    if(count > 0) {
      final String product = parseProduct(productPart);
      result.put(product, count);
    }
  }

  private int parseCount(String rawCount) {
    try {
      return Integer.parseInt(rawCount);
    } catch(NumberFormatException nfe) {
      return -1;
    }
  }
  
  private String parseProduct(String product) {
    if(product.startsWith("\"")) {
      product = product.substring(1);
    }
    if(product.endsWith("\"")) {
      product = product.substring(0, product.length() - 1);
    }
    return product;
  }
}
