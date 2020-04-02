package com.almworks.bugzilla.integration;

import com.almworks.api.http.*;
import com.almworks.api.install.Setup;
import com.almworks.http.HttpLoaderFactoryImpl;
import com.almworks.misc.TestWorkArea;
import com.almworks.util.tests.BaseTestCase;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.server.*;

import java.io.IOException;
import java.io.InputStream;

public abstract class HttpBasedFixture extends BaseTestCase {
  protected SimpleHttpServer myServer;
  protected HttpClient myClient;
  protected HttpMaterial myMaterial;
  private static final String TEST_CONTEXT = "/unittest/";

  protected void setUp() throws Exception {
    super.setUp();
    Setup.cleanupForTestCase();
    Setup.setHomeDir(createTempDir());
    BugzillaEnv.cleanUpForTestCase();
    BugzillaEnv.setupBugzillaEnv(new TestWorkArea());
    myMaterial = new DefaultHttpMaterial(HttpClientProvider.SIMPLE, new HttpLoaderFactoryImpl());
    myMaterial.setCharset("UTF-8");
    myServer = new SimpleHttpServer(0);
    myServer.setHttpService(new ResourceAccessService());
  }

  protected void tearDown() throws Exception {
    myServer.destroy();
    myServer = null;
    BugzillaEnv.cleanUpForTestCase();
    Setup.cleanupForTestCase();
    super.tearDown();
  }

  protected String getTestUrl(String resourceFileName) {
    return getTestUrlBase() + getClass().getName() + "?load=" + resourceFileName;
  }

  private String getTestUrlBase() {
    return "http://localhost:" + myServer.getLocalPort() + TEST_CONTEXT;
  }

  protected class ResourceAccessService implements HttpService {
    public boolean process(SimpleRequest request, SimpleResponse response) throws IOException {
      String uri = request.getRequestLine().getUri();
      String urlBase = TEST_CONTEXT;
      assertTrue(uri.startsWith(urlBase));
      uri = uri.substring(urlBase.length());
      int question = uri.indexOf("?load=");
      String className = uri.substring(0, question);
      int and = uri.indexOf('&', question + 1);
      String fileName = uri.substring(question + 6, and < 0 ? uri.length() : and);
      Class myClass = HttpBasedFixture.this.getClass();
      assertTrue(myClass.getName().equals(className));
      InputStream readFile = myClass.getResourceAsStream(fileName);
      if (readFile == null) {
        response.setStatusLine(HttpVersion.HTTP_1_0, 404, "Not found");
        response.setBodyString("file " + fileName + " is not found");
        return true;
      }
      response.setBody(readFile);
      return true;
    }
  }
}