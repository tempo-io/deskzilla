package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.HttpMaterial;
import com.almworks.util.RunnableRE;
import org.jetbrains.annotations.*;

public class CountQuery extends BugzillaOperation {
  private final String myQueryUrl;

  public CountQuery(HttpMaterial material, String queryUrl, @Nullable AuthenticationMaster authenticationMaster) {
    super(material, authenticationMaster);
    myQueryUrl = queryUrl;
  }

  public int count() throws ConnectorException {
    return runOperation(new RunnableRE<Integer, ConnectorException>() {
      public Integer run() throws ConnectorException {
        String url = LoadQuery.addCsvCtype(myQueryUrl);
        DocumentLoader loader = getDocumentLoader(url, true);
        loader.addRedirectUriHandler(LoadQuery.CTYPE_REDIRECT_VERIFIER);
        try {
          loader.httpGET();
        } catch (ConnectorException e) {
          return -1;
        }
        String csvString = loader.loadString();
        int lines = 0;
        int k = -1;
        while ((k = csvString.indexOf("\n", k + 1)) >= 0)
          lines++;
        return lines;
      }
    });
  }
}
