package com.almworks.api.http;

import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.commons.Condition;
import org.almworks.util.*;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.jetbrains.annotations.*;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.BufferOverflowException;
import java.util.*;

public class HttpUtils {
  public static final Date COOKIE_EXPIRED = new Date(0);
  public static final Date COOKIE_NOT_EXPIRED = new Date(0x7FFFFFFF00000000L);
  private static final String CHARSET = "UTF-8";

  private static String CACHE_LOCAL_HOSTNAME;

  public static String adjustPathForCookie(String path) {
    if (path == null || path.length() == 0) {
      path = "/";
    } else {
      int k = path.lastIndexOf('/');
      if (k == -1 || k == 0)
        path = "/";
      else
        path = path.substring(0, k);
    }
    return path;
  }

  public static Pair<String, String> getNTDomainUsername(String username) {
    if (username == null)
      return null;
    username = username.trim();
    String domain = "";
    int slash = username.lastIndexOf('\\');
    if (slash > 0) {
      domain = username.substring(0, slash).trim();
      username = username.substring(slash + 1).trim();
    }
    return Pair.create(domain, username);
  }

  public static boolean areNTUsernamesEquals(String u1, String u2, boolean ignoreEmptyDomain) {
    Pair<String, String> p1 = getNTDomainUsername(u1);
    Pair<String, String> p2 = getNTDomainUsername(u2);
    if (p1 == null || p2 == null)
      return p1 == p2;
    if (!p1.getSecond().equalsIgnoreCase(p2.getSecond()))
      return false;

    String dom1 = p1.getFirst();
    String dom2 = p2.getFirst();
    if (ignoreEmptyDomain && (dom1.length() == 0 || dom2.length() == 0))
      return true;

    return dom1.equalsIgnoreCase(dom2);
  }

  public static String getHeaderAuxiliaryInfo(Header header, String key) {
    if (header == null || key == null)
      return null;
    return getHeaderAuxiliaryInfo(header.getValue(), key);
  }

  public static String getHeaderAuxiliaryInfo(String value, String key) {
    if (value == null || key == null || key.length() == 0)
      return null;
    int k = value.indexOf(';');
    String upperKey = Util.upper(key);
    while (k >= 0) {
      int nextk = value.indexOf(';', k + 1);
      String spec = nextk > k ? value.substring(k + 1, nextk) : value.substring(k + 1);
      k = nextk;
      spec = spec.trim();
      if (!Util.upper(spec).startsWith(upperKey))
        continue;
      int i = key.length();
      for (; i < spec.length(); i++)
        if (!Character.isWhitespace(spec.charAt(i)))
          break;
      if (spec.charAt(i) != '=')
        continue;
      spec = spec.substring(i + 1).trim();
      if (spec.length() > 0 && spec.charAt(0) == '"' && spec.charAt(spec.length() - 1) == '"')
        spec = spec.substring(1, spec.length() - 1);
      else if (spec.length() > 0 && spec.charAt(0) == '\'' && spec.charAt(spec.length() - 1) == '\'')
        spec = spec.substring(1, spec.length() - 1);
      return spec;
    }
    return null;
  }

  public static Collection<Cookie> removeCookies(HttpState state, Condition<Cookie> removeCondition) {
    List<Cookie> removed = null;
    Cookie[] cookies = state.getCookies();
    state.clearCookies();
    for (Cookie c : cookies) {
      if (!removeCondition.isAccepted(c)) {
        state.addCookie(c);
      } else {
        if (removed == null)
          removed = Collections15.arrayList();
        removed.add(c);
      }
    }
    return removed == null ? Collections15.<Cookie>emptyList() : removed;
  }

  public static Collection<Cookie> removeCookies(HttpState state, final String... names) {
    if (names.length == 0)
      return Collections15.emptyList();
    return removeCookies(state, new Condition<Cookie>() {
      public boolean isAccepted(Cookie cookie) {
        String cookieName = cookie.getName();
        for (String name : names) {
          if (cookieName.equalsIgnoreCase(name))
            return true;
        }
        return false;
      }
    });
  }

  public static Cookie getCookie(HttpState state, String name) {
    Cookie[] cookies = state.getCookies();
    for (Cookie cookie : cookies) {
      if (name.equalsIgnoreCase(cookie.getName()))
        return cookie;
    }
    return null;
  }

  @NotNull
  public static List<Cookie> getCookies(HttpState state, String name) {
    Cookie[] cookies = state.getCookies();
    List<Cookie> result = null;
    for (Cookie cookie : cookies) {
      if (name.equalsIgnoreCase(cookie.getName())) {
        if (result == null)
          result = Collections15.arrayList();
        result.add(cookie);
      }
    }
    return result == null ? Collections15.<Cookie>emptyList() : result;
  }

  public static URI addGetParameterIfMissing(URI url, String parameter, @NotNull String unescapedValue) {
    try {
      String newUrl = addGetParameterIfMissing(url.getEscapedURI(), parameter, unescapedValue);
      return new URI(newUrl, true);
    } catch (URIException e) {
      Log.warn(e);
      return url;
    }
  }

  /**
   * @return escaped URL
   */
  public static String addGetParameterIfMissing(String escapedUrl, String parameter, @NotNull String unescapedValue) {
    try {
      URI uri = new URI(escapedUrl, true);
      String query = uri.getEscapedQuery();
      if (query != null) {
        String[] pairs = query.split("[\\?&]+");
        String seek = parameter + "=";
        for (String pair : pairs) {
          if (pair.startsWith(seek)) {
            return escapedUrl;
          }
        }
      }
      StringBuilder buffer = new StringBuilder(escapedUrl);
      addGetParameter(buffer, parameter, unescapedValue);
      return buffer.toString();
    } catch (URIException e) {
      Log.warn(e);
      return escapedUrl;
    }
  }

  public static String addGetParameter(String escapedUrl, @NotNull String parameter, @NotNull String unescapedValue) {
    StringBuilder b = new StringBuilder(escapedUrl);
    addGetParameter(b, parameter, unescapedValue);
    return b.toString();
  }

  public static void addGetParameter(StringBuilder escapedUrl, @NotNull String parameter,
    @NotNull String unescapedValue)
  {
    escapedUrl.append(escapedUrl.indexOf("?") >= 0 ? '&' : '?');
    escapedUrl.append(parameter);
    escapedUrl.append('=');
    escapedUrl.append(encode(unescapedValue));
  }

  public static String encode(String value) {
    try {
      return URLEncoder.encode(value, CHARSET);
    } catch (UnsupportedEncodingException e) {
      Log.error(e);
      return value;
    }
  }

  public static String decode(String value) {
    try {
      return URLDecoder.decode(value, CHARSET);
    } catch (UnsupportedEncodingException e) {
      Log.error(e);
      return value;
    }
  }

  /**
   * Returns absolute URL made from possibly local URL, using base URL.
   *
   * @param localEscapedUrl the URL that may be local (i.e. "/some/url")
   * @param baseEscapedUrl  the URL of the page where localURL is found
   * @return escaped Url of the final page
   */
  public static String getAbsoluteUrl(String localEscapedUrl, String baseEscapedUrl) {
    try {
      URI uri = new URI(localEscapedUrl, true);
      if (uri.isAbsoluteURI())
        return localEscapedUrl;
      URI baseUri = new URI(baseEscapedUrl, true);
      if (!baseUri.isAbsoluteURI())
        return localEscapedUrl;
      uri = new URI(baseUri, uri);
      return uri.getEscapedURI();
    } catch (URIException e) {
      Log.warn(e);
      return localEscapedUrl;
    } catch (BufferOverflowException e) {
      // got this in exception trace
      Log.warn(e);
      return localEscapedUrl;
    }
  }

  public static String escapeUrl(String unescapedUrl) {
    try {
      return new URI(unescapedUrl, false).getEscapedURI();
    } catch (URIException e) {
      Log.warn(unescapedUrl, e);
      return unescapedUrl;
    }
  }

  public static MultiMap<String, Cookie> copyCookies(Cookie[] cookies) {
    MultiMap<String, Cookie> result = MultiMap.create();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        String name = cookie.getName();
        result.add(name,
          new Cookie(cookie.getDomain(), name, cookie.getValue(), cookie.getPath(), cookie.getExpiryDate(),
            cookie.getSecure()));
      }
    }
    return result;
  }

  public static String cookieDump(Cookie cookie) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(cookie.getName()).append('=').append(cookie.getValue());
    buffer.append("; domain=").append(cookie.getDomain()).append("; path=").append(cookie.getPath());
    buffer.append("; secure=").append(cookie.getSecure()).append("; expires=").append(cookie.getExpiryDate());
    return buffer.toString();
  }

  public static GetMethod createGet(String url) throws HttpMethodFactoryException {
    try {
      return new GetMethod(url);
    } catch (IllegalArgumentException e) {
      throw new HttpMethodFactoryException("cannot GET " + url, e);
    }
  }

  public static PostMethod createPost(String url) throws HttpMethodFactoryException {
    try {
      return new PostMethod(url);
    } catch (IllegalArgumentException e) {
      throw new HttpMethodFactoryException("cannot POST " + url, e);
    }
  }

  public static String getEngineVersion() {
    // no way to get real version
    return "Jakarta Commons-HttpClient/3.0";
  }

  public static String toString(HttpMethod method) {
    StringBuilder builder = new StringBuilder(42);
    builder.append(method.getName()).append(' ');
    try {
      builder.append(method.getURI());
    } catch (URIException e) {
      builder.append("?");
    }
    return builder.toString();
  }

  public static String getLocalHostname() {
    if (CACHE_LOCAL_HOSTNAME == null) {
      CACHE_LOCAL_HOSTNAME = calcLocalHostname();
      Log.debug("local hostname set to (" + CACHE_LOCAL_HOSTNAME + ")");
    }
    return CACHE_LOCAL_HOSTNAME;
  }

  @NotNull
  private static String calcLocalHostname() {
    try {
      InetAddress localHost = InetAddress.getLocalHost();
      if (localHost == null) {
        Log.warn("localhost IP is null");
        return "";
      }
      String r = localHost.getHostName();
      Log.debug("local hostname (" + r + ")");
      if (r == null)
        return "";
      int dot = r.indexOf('.');
      if (dot >= 0) {
        r = r.substring(0, dot);
      }
      int v = Util.toInt(r, -1);
      if (v > 0 && v < 256) {
        // IP address?
        r = "";
      }
      return r;
    } catch (UnknownHostException e) {
      Log.warn(e);
      return "";
    }
  }

  public static List<String> cookieDump(Cookie[] cookies) {
    List<String> list = Collections15.arrayList();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        list.add(cookieDump(cookie));
      }
    }
    Collections.sort(list);
    return list;
  }

  /**
   * Copied from{@link org.apache.commons.httpclient.HttpMethodBase#getCookieSpec}
   */
  public static CookieSpec getCookieSpec(HttpState state, HttpMethodBase method) {
    CookieSpec cookiespec;
    int i = state.getCookiePolicy();
    if (i == -1) {
      cookiespec =
        org.apache.commons.httpclient.cookie.CookiePolicy.getCookieSpec(method.getParams().getCookiePolicy());
    } else {
      cookiespec = org.apache.commons.httpclient.cookie.CookiePolicy.getSpecByPolicy(i);
    }
    cookiespec.setValidDateFormats((Collection) method.getParams().getParameter(HttpMethodParams.DATE_PATTERNS));
    return cookiespec;
  }

  public static Cookie[] matchCookies(CookieSpec spec, HttpClient client, HttpMethodBase method, Cookie[] cookies)
    throws URIException
  {
    if (cookies == null)
      return cookies;
    HostConfiguration hostconfig = createHostConfiguration(client, method);
    String host = getCookieHost(hostconfig, method);
    int port = hostconfig.getPort();
    if (port < 0)
      port = 80;
    Cookie[] matched = spec.match(host, port, method.getPath(), hostconfig.getProtocol().isSecure(), cookies);
    return matched;
  }

  public static String getCookieHost(HostConfiguration hostconfig, HttpMethodBase method) {
    String host = method.getParams().getVirtualHost();
    if (host == null) {
      host = hostconfig.getHost();
    }
    return host;
  }

  public static HostConfiguration createHostConfiguration(HttpClient client, HttpMethodBase method)
    throws URIException
  {
    HostConfiguration hostconfig = new HostConfiguration(client.getHostConfiguration());
    URI uri = method.getURI();
    assert uri.isAbsoluteURI();
    if (uri.isAbsoluteURI()) {
      hostconfig.setHost(uri);
    }
    return hostconfig;
  }

  public static boolean isSameCookie(Cookie f, Cookie t) {
    if (f == t)
      return true;
    if (t == null || t == null)
      return false;
    return f.getSecure() == t.getSecure() && Util.equals(f.getDomain(), t.getDomain()) &&
      Util.equals(f.getName(), t.getName()) && Util.equals(f.getValue(), t.getValue()) &&
      Util.equals(f.getPath(), t.getPath()) && Util.equals(f.getExpiryDate(), t.getExpiryDate());
  }

  /**
   * Removes cookies that are "covered" by a more generic cookie.
   */
  public static Cookie[] normalizeCookies(Cookie[] cookies) {
    if (cookies == null || cookies.length == 0)
      return cookies;
    boolean remove = false;
  CHECK:
    for (int i = 0; i < cookies.length - 1; i++) {
      for (int j = i + 1; j < cookies.length; j++) {
        if (isCookieCovered(cookies[i], cookies[j]) || isCookieCovered(cookies[j], cookies[i])) {
          remove = true;
          break CHECK;
        }
      }
    }
    if (!remove)
      return cookies;
    List<Cookie> r = Collections15.arrayList(Arrays.asList(cookies));
    for (int i = 0; i < r.size() - 1; i++) {
      Cookie ci = r.get(i);
      for (int j = i + 1; j < r.size(); j++) {
        Cookie cj = r.get(j);
        if (isCookieCovered(ci, cj)) {
          r.remove(j);
          j--;
        } else if (isCookieCovered(cj, ci)) {
          r.remove(i);
          i--;
          break;
        }
      }
    }
    return r.toArray(new Cookie[r.size()]);
  }

  private static boolean isCookieCovered(Cookie superCookie, Cookie subCookie) {
    if (!Util.equals(superCookie.getName(), subCookie.getName()))
      return false;
    if (!Util.equals(superCookie.getValue(), subCookie.getValue()))
      return false;
    if (!Util.equals(superCookie.getDomain(), subCookie.getDomain()))
      return false;
    if (superCookie.getSecure() != subCookie.getSecure())
      return false;
    String superPath = Util.NN(superCookie.getPath(), "/");
    String subPath = Util.NN(subCookie.getPath(), "/");
    if (!subPath.startsWith(superPath))
      return false;
    Date superExp = superCookie.getExpiryDate();
    Date subExp = subCookie.getExpiryDate();
    long superTime = superExp == null ? Long.MAX_VALUE : superExp.getTime();
    long subTime = subExp == null ? Long.MAX_VALUE : subExp.getTime();
    if (subTime > superTime)
      return false;
    return true;
  }

  public static boolean hasCookie(Cookie[] cookies, String name, String value) {
    if (cookies == null || cookies.length == 0)
      return false;
    for (Cookie cookie : cookies) {
      if (Util.equals(name, cookie.getName()) && Util.equals(value, cookie.getValue())) {
        return true;
      }
    }
    return false;
  }
}
