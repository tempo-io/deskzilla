/*
 * $Header$
 * $Revision: 178 $
 * $Date: 2004-12-25 04:54:53 +0300 (Sat, 25 Dec 2004) $
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
 * This request handler guards access to the http server when used in a request handler
 * chain. It checks the headers for valid credentials and performs the
 * authentication handshake if necessary.
 *
 * @author Ortwin Glueck
 * @author Oleg Kalnichevski
 */
public class AuthRequestHandler implements HttpRequestHandler {

  private Credentials credentials = null;
  private String realm = null;
  private boolean keepalive = true;

  /**
   * The authenticate response header.
   */
  public static final String AUTH_RESP = "Authorization";

  /**
   * TODO replace creds parameter with a class specific to an auth scheme
   * encapsulating all required information for a specific scheme
   *
   * @param creds
   */
  public AuthRequestHandler(final Credentials creds, final String realm, boolean keepalive) {
    if (creds == null)
      throw new IllegalArgumentException("Credentials may not be null");
    this.credentials = creds;
    this.keepalive = keepalive;
    if (realm != null) {
      this.realm = realm;
    } else {
      this.realm = "test";
    }
  }

  public AuthRequestHandler(final Credentials creds, final String realm) {
    this(creds, realm, true);
  }

  public AuthRequestHandler(final Credentials creds) {
    this(creds, null, true);
  }

  public boolean processRequest(final SimpleHttpServerConnection conn,
    final SimpleRequest request) throws IOException {
    Header clientAuth = request.getFirstHeader(AUTH_RESP);
    if (clientAuth != null && checkAuthorization(clientAuth)) {
      return false;
    } else {
      SimpleResponse response = performBasicHandshake(conn, request);
      // Make sure the request body is fully consumed
      request.getBodyBytes();
      conn.writeResponse(response);
      return true;
    }
  }

  //TODO add more auth schemes
  private SimpleResponse performBasicHandshake(final SimpleHttpServerConnection conn,
    final SimpleRequest request) throws IOException {
    SimpleResponse response = new SimpleResponse();
    response.setStatusLine(request.getRequestLine().getHttpVersion(),
      HttpStatus.SC_UNAUTHORIZED);
    if (!request.getRequestLine().getMethod().equalsIgnoreCase("HEAD")) {
      response.setBodyString("unauthorized");
    }
    response.addHeader(new Header("WWW-Authenticate", "basic realm=\"" + this.realm + "\""));
    if (this.keepalive) {
      response.addHeader(new Header("Connection", "keep-alive"));
      conn.setKeepAlive(true);
    } else {
      response.addHeader(new Header("Connection", "close"));
      conn.setKeepAlive(false);
    }
    return response;
  }

  /**
   * Checks if the credentials provided by the client match the required
   * credentials
   *
   * @param clientAuth
   * @return true if the client is authorized, false if not.
   */
  private boolean checkAuthorization(final Header clientAuth) {
    String expectedAuthString = BasicScheme.authenticate((UsernamePasswordCredentials) credentials,
      "ISO-8859-1");
    return expectedAuthString.equals(clientAuth.getValue());
  }

}
