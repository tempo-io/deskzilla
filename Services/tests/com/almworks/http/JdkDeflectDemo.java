package com.almworks.http;

import com.almworks.api.http.*;
import com.almworks.api.http.auth.HttpAuthChallengeData;
import com.almworks.api.http.auth.HttpAuthCredentials;
import com.almworks.util.Pair;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.ui.actions.ActionContext;
import com.almworks.util.ui.actions.CantPerformException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;

public class JdkDeflectDemo {
  public static void main(String[] args) throws HttpLoaderException, IOException {
    System.setProperty("http.auth.scheme.priority", "basic");


    DefaultHttpMaterial material = createMaterial();
    final String url = "http://iistest.in.almworks.com/icons/box.gif";
    HttpLoaderImpl loader = new HttpLoaderImpl(material, new HttpMethodFactory() {
      public HttpMethodBase create() throws HttpMethodFactoryException {
        return new GetMethod(url);
      }
    }, url);

    material.setFeedbackHandler(new FeedbackHandler() {
      @CanBlock
      public HttpAuthCredentials requestCredentials(HttpAuthChallengeData data, HttpAuthCredentials failedCredentials,
        boolean quiet)
        throws InterruptedException, HttpCancelledException
      {
        return new HttpAuthCredentials("iistest", "iistest");
      }

      @CanBlock
      public Pair<HttpAuthCredentials, String> requestPreliminaryCredentials(String host, int port, boolean proxy) {
        return null;
      }
    });

    HttpResponseData data = loader.load();
    System.out.println("data.getContentLength() = " + data.getContentLength());
    System.out.println("data.getContentFilename() = " + data.getContentFilename());
    System.out.println("data.getContentType() = " + data.getContentType());
    System.out.println("data.getFullContentType() = " + data.getFullContentType());
    System.out.println("data.getLastURI() = " + data.getLastURI());
    System.out.println("data.getResponseHeaders() = " + data.getResponseHeaders());
    byte[] bytes = data.transferToBytes(null);
    System.out.println("bytes.length = " + bytes.length);
  }

  private static DefaultHttpMaterial createMaterial() {
    return new DefaultHttpMaterial(new HttpClientProviderImpl(new MyHttpProxyInfo("expo", 3128, "proxyc", "c")), new HttpLoaderFactoryImpl());
  }

  private static class MyHttpProxyInfo implements HttpProxyInfo {
    private final String myHost;
    private final int myPort;
    private final String myUser;
    private final String myPass;


    private MyHttpProxyInfo() {
      this(null, -1, null, null);
    }

    public MyHttpProxyInfo(String host, int port) {
      this(host, port, null, null);
    }

    public MyHttpProxyInfo(String host, int port, String user, String pass) {
      myHost = host;
      myPort = port;
      myUser = user;
      myPass = pass;
    }

    public boolean isUsingProxy() {
      return myHost != null;
    }

    public String getProxyHost() {
      return myHost;
    }

    public int getProxyPort() {
      return myPort;
    }

    public void editProxySettings(ActionContext context) throws CantPerformException {

    }

    public boolean isAuthenticatedProxy() {
      return myHost != null && myUser != null && myPass != null;
    }

    public String getProxyUser() {
      return myUser;
    }

    public String getProxyPassword() {
      return myPass;
    }

    public Modifiable getModifiable() {
      return Modifiable.NEVER;
    }
  }
}
