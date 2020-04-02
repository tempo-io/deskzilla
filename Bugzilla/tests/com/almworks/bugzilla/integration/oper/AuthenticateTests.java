package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.bugzilla.integration.HttpBasedFixture;
import com.almworks.util.Pair;
import org.jdom.Document;

import java.util.List;
import java.util.Locale;

public class AuthenticateTests extends HttpBasedFixture {
  private static final String USERNAME = "user";
  private static final String PASSWORD = "pass";

  public void testFindLoginPageTurkishRegression() throws ConnectorException {
    Locale locale = Locale.getDefault();
    Locale.setDefault(new Locale("tr", "TR"));
    try {
      String url = getTestUrl("Authenticate-Page.html");
      DocumentLoader loader = new DocumentLoader(myMaterial, url, null);
      Document document = loader.httpGET().loadHTML();

      Authenticate authenticate = new Authenticate(myMaterial, "", USERNAME, PASSWORD, null);
      List<Pair<String, String>> loginForm = authenticate.buildLoginParams(document);
      assertNotNull(loginForm);
      boolean hasUsername = false;
      boolean hasPassword = false;
      for (Pair<String, String> pair : loginForm) {
        if ("bugzilla_login".equalsIgnoreCase(pair.getFirst())) {
          assertEquals(USERNAME, pair.getSecond());
          hasUsername = true;
        }
        if ("bugzilla_password".equalsIgnoreCase(pair.getFirst())) {
          assertEquals(PASSWORD, pair.getSecond());
          hasPassword = true;
        }
      }
      assertTrue(hasUsername);
      assertTrue(hasPassword);
    } finally {
      Locale.setDefault(locale);
    }
  }
}
