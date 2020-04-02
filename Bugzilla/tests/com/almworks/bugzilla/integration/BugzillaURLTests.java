package com.almworks.bugzilla.integration;

import com.almworks.api.http.HttpClientProvider;
import com.almworks.http.HttpLoaderFactoryImpl;
import com.almworks.util.tests.BaseTestCase;

import java.net.MalformedURLException;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugzillaURLTests extends BaseTestCase {

  private void doTest(String inputURL, String mustbeURL) throws MalformedURLException {
    try {
      assertEquals(mustbeURL, new BugzillaIntegration(inputURL, HttpClientProvider.SIMPLE, new HttpLoaderFactoryImpl(), null, null, null, null).
        getBaseURL());
    } catch (MalformedURLException e) {
      if (mustbeURL == null) {
        // ok!
      } else {
        throw e;
      }
    }
  }

  public void testGoodURLs() throws MalformedURLException {
    doTest("http://my.stupid.host/bugzilla", "http://my.stupid.host/bugzilla/");
    doTest("my.stupid.host/bugzilla  ", "http://my.stupid.host/bugzilla/");
    doTest(" my.stupid.host/bugzilla/index.cgi?", "http://my.stupid.host/bugzilla/");
    doTest("http://my.stupid.host/bugzilla/index.cgi?watch=e", "http://my.stupid.host/bugzilla/");
  }

  public void testBadURLs() throws MalformedURLException {
    doTest(":/my.stupid.host/bugzilla/index.cgi?watch=e", null);
    doTest(null, null);
    doTest("", null);
    doTest("     ", null);
    doTest("     ", null);
  }

}
