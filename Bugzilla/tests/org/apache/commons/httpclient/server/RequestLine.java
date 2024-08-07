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

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * Defines a HTTP request-line, consisting of method name, URI and protocol.
 * Instances of this class are immutable.
 *
 * @author Christian Kohlschuetter
 * @author Oleg Kalnichevski
 */
public class RequestLine {

  private HttpVersion httpversion = null;
  private String method = null;
  private String uri = null;

  public static RequestLine parseLine(final String l)
    throws HttpException {
    String method = null;
    String uri = null;
    String protocol = null;
    try {
      StringTokenizer st = new StringTokenizer(l, " ");
      method = st.nextToken();
      uri = st.nextToken();
      protocol = st.nextToken();
    } catch (NoSuchElementException e) {
      throw new ProtocolException("Invalid request line: " + l);
    }
    return new RequestLine(method, uri, protocol);
  }

  public RequestLine(final String method, final String uri, final HttpVersion httpversion) {
    super();
    if (method == null) {
      throw new IllegalArgumentException("Method may not be null");
    }
    if (uri == null) {
      throw new IllegalArgumentException("URI may not be null");
    }
    if (httpversion == null) {
      throw new IllegalArgumentException("HTTP version may not be null");
    }
    this.method = method;
    this.uri = uri;
    this.httpversion = httpversion;
  }

  public RequestLine(final String method, final String uri, final String httpversion)
    throws ProtocolException {
    this(method, uri, HttpVersion.parse(httpversion));
  }

  public String getMethod() {
    return this.method;
  }

  public HttpVersion getHttpVersion() {
    return this.httpversion;
  }

  public String getUri() {
    return this.uri;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(this.method);
    sb.append(" ");
    sb.append(this.uri);
    sb.append(" ");
    sb.append(this.httpversion);
    return sb.toString();
  }
}
