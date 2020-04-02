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
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.commons.httpclient.server;

import org.apache.commons.httpclient.*;

import java.io.*;
import java.net.Socket;

/**
 * This request handler can handle the CONNECT method. It does nothing for any
 * other HTTP methods.
 *
 * @author Ortwin Glueck
 */
public class TransparentProxyRequestHandler implements HttpRequestHandler {

  /*
   * (non-Javadoc)
   *
   * @see org.apache.commons.httpclient.server.HttpRequestHandler#processRequest(org.apache.commons.httpclient.server.SimpleHttpServerConnection)
   */
  public boolean processRequest(final SimpleHttpServerConnection conn,
    final SimpleRequest request) throws IOException {
    RequestLine line = request.getRequestLine();
    String method = line.getMethod();
    if (!"CONNECT".equalsIgnoreCase(method))
      return false;
    URI url = new HttpURL(line.getUri());
    handshake(conn, url);
    return true;
  }

  private void handshake(SimpleHttpServerConnection conn, URI url) throws IOException {
    Socket targetSocket = new Socket(url.getHost(), url.getPort());
    InputStream sourceIn = conn.getInputStream();
    OutputStream sourceOut = conn.getOutputStream();
    InputStream targetIn = targetSocket.getInputStream();
    OutputStream targetOut = targetSocket.getOutputStream();

    ResponseWriter out = conn.getWriter();
    out.println("HTTP/1.1 200 Connection established");
    out.flush();

    BidiStreamProxy bdsp = new BidiStreamProxy(sourceIn, sourceOut, targetIn, targetOut);
    bdsp.start();
    try {
      bdsp.block();
    } catch (InterruptedException e) {
      throw new IOException(e.toString());
    }
  }

  private void sendHeaders(Header[] headers, OutputStream os) throws IOException {
    Writer out;
    try {
      out = new OutputStreamWriter(os, "US-ASCII");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e.toString());
    }
    for (int i = 0; i < headers.length; i++) {
      out.write(headers[i].toExternalForm());
    }
  }
}
