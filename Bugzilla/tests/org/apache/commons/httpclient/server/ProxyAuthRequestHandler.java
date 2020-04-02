/*
 * $Header$
 * $Revision: 5069 $
 * $Date: 2010-03-15 16:32:42 +0300 (Mon, 15 Mar 2010) $
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.commons.httpclient.server;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.BasicScheme;

import java.io.IOException;

/**
 * This request handler guards access to a proxy when used in a request handler
 * chain. It checks the headers for valid credentials and performs the
 * authentication handshake if necessary.
 *
 * @author Ortwin Glueck
 * @author Oleg Kalnichevski
 */
public class ProxyAuthRequestHandler implements HttpRequestHandler {
  private Credentials credentials;

  /**
   * The proxy authenticate response header.
   */
  public static final String PROXY_AUTH_RESP = "Proxy-Authorization";

  /**
   * TODO replace creds parameter with a class specific to an auth scheme
   * encapsulating all required information for a specific scheme
   *
   * @param creds
   */
  public ProxyAuthRequestHandler(Credentials creds) {
    if (creds == null)
      throw new IllegalArgumentException("Credentials cannot be null");
    this.credentials = creds;
  }

  public boolean processRequest(final SimpleHttpServerConnection conn,
    final SimpleRequest request) throws IOException {
    Header clientAuth = request.getFirstHeader(PROXY_AUTH_RESP);
    if (clientAuth != null) {
      return !checkAuthorization(clientAuth);
    } else {
      SimpleResponse response = performBasicHandshake(request);
      // Make sure the request body is fully consumed
      request.getBodyBytes();
      conn.writeResponse(response);
      return true;
    }
  }

  //TODO add more auth schemes
  private SimpleResponse performBasicHandshake(final SimpleRequest request) {
    SimpleResponse response = new SimpleResponse();
    response.setStatusLine(request.getRequestLine().getHttpVersion(),
      HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED);
    response.addHeader(new Header("Proxy-Authenticate", "basic realm=test"));
    //response.addHeader(new Header("Proxy-Connection", "Close"));
    return response;
  }

  /**
   * Checks if the credentials provided by the client match the required
   * credentials
   *
   * @param clientAuth
   * @return true if the client is authorized, false if not.
   */
  private boolean checkAuthorization(Header clientAuth) {
    String expectedAuthString = BasicScheme.authenticate((UsernamePasswordCredentials) credentials,
      "ISO-8859-1");
    return expectedAuthString.equals(clientAuth.getValue());
  }

}
