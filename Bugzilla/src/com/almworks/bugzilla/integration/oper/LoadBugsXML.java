package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.CannotParseException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.http.*;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Procedure;
import com.almworks.util.io.StringTransferTracker;
import com.almworks.util.progress.Progress;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Loads a number of bugs through XML capability of Bugzilla.
 *
 * @author sereda
 */
public class LoadBugsXML extends BugzillaOperation {
  private final ServerInfo myServerInfo;
  private final Integer[] myIDs;
  private final boolean myForTest;
  private final boolean myAttachmentsExcluded;
  private int myNextIDIndex = 0;
  private final String myExtraParams;

  @Nullable
  private final Progress myProgress;

  @Nullable
  private final Progress[] myRequestProgresses;

  private long myLastPageLoad;

  private Function<Long, Long> myGuessedServerOffsetConvertor;
  private RedirectURIHandler myRedirectHandler = new RedirectURIHandler() {
    @Nullable
    public URI approveRedirect(URI initialUri, URI redirectUri) throws HttpLoaderException {
      if (redirectUri == null)
        return redirectUri;
      try {
        String uri = redirectUri.getEscapedURI();
        updateStrategy(uri);
        if (myExtraParams == null)
          return redirectUri;
        if (uri.indexOf(myExtraParams) >= 0)
          return redirectUri;
        if (uri.length() == 0)
          return redirectUri;
        uri = uri + (uri.indexOf('?') >= 0 ? '&' : '?') + myExtraParams;
        return new URI(uri, true);
      } catch (URIException e) {
        Log.warn(e);
        return redirectUri;
      }
    }
  };

  private static final String LOAD_STRATEGY = "LoadBugsXML.strategy";
  private static final BugzillaVersion BZ_VER_FLAG_USER_LONG = BugzillaVersion.V3_6;
  private static final BugzillaVersion BZ_VER_NO_XML_CGI = BugzillaVersion.V4_2;

  public LoadBugsXML(ServerInfo serverInfo, Integer[] IDs, Progress progress, String extraParams, boolean forTest,
    boolean attachmentsExcluded)
  {
    super(serverInfo);
    myServerInfo = serverInfo;
    myExtraParams = extraParams;
    myIDs = IDs;
    myForTest = forTest;
    myAttachmentsExcluded = attachmentsExcluded;
    Arrays.sort(myIDs);

    int numOfRequests =
      (myIDs.length + BugzillaHTMLConstants.BUG_FIELDS_URL_MAX_IDS) / BugzillaHTMLConstants.BUG_FIELDS_URL_MAX_IDS;

    final int totalCount = myIDs.length;
//    myProgress = ProgressAggregator.createWithSourceCount(LoadBugsXML.class.getName(), numOfRequests,
//      new MyActivityProducer(totalCount));
    myProgress = progress;
    if (numOfRequests > 0 && myProgress != null) {
      myRequestProgresses = new Progress[numOfRequests];
      float span = 1F / numOfRequests;
      for (int i = 0; i < numOfRequests; i++) {
        myRequestProgresses[i] = myProgress.createDelegate(span, "LBXML." + (i + 1));
      }
    } else {
      myRequestProgresses = null;
    }
  }

  private void updateStrategy(String redirectUri) {
    if (redirectUri.contains("show_bug.cgi") && redirectUri.contains("ctype=xml")) {
      long currentStrategy = myServerInfo.getStateStorage().getPersistentLong(LOAD_STRATEGY);
      if (currentStrategy != 0) {
        Log.warn(this + ": got redirect with current strategy set to " + currentStrategy);
      } else {
        myServerInfo.getStateStorage().setPersistentLong(LOAD_STRATEGY, 1);
      }
    }
  }

  public List<BugInfo> loadBugs() throws ConnectorException {
    final List<BugInfo> r = Collections15.arrayList();
    loadBugsInto(new Procedure<BugInfo>() {
      public void invoke(BugInfo arg) {
        r.add(arg);
      }
    });
    return r;
  }


  public void loadBugsInto(final Procedure<BugInfo> sink) throws ConnectorException {
    boolean watchProgress = myProgress != null && myRequestProgresses != null;
    int request = 0;
    try {
      while (true) {
        myMaterial.checkCancelled();

        Pair<String, Integer> pair = buildNextURL();
        if (pair == null)
          break;

        final String url = pair.getFirst();
        int count = pair.getSecond().intValue();

        final StringTransferTracker tracker;
        Progress progress = null;
        if (watchProgress && request < myRequestProgresses.length) {
          progress = myRequestProgresses[request++];
          tracker = new BugTracker(progress, count);
        } else {
          tracker = null;
        }

        try {
          Log.debug("loading url " + url);
          try {
            runOperation(new RunnableRE<Void, ConnectorException>() {
              public Void run() throws ConnectorException {
                try {
                  DocumentLoader loader = getDocumentLoader(url, true, "xml");
                  loader.setTransferTracker(tracker);
                  loader.addRedirectUriHandler(myRedirectHandler);
                  pauseForMinimumDelay();
                  loader.httpGET();
                  Document document = loadXMLSafe(loader, url);
                  boolean fix214 = isBugzilla214(document);
                  List<Element> bugList = document.getRootElement().getChildren(BugzillaHTMLConstants.XML_TAG_BUG);
                  for (Iterator<Element> iterator = bugList.iterator(); iterator.hasNext();) {
                    myMaterial.checkCancelled();
                    BugInfo bug = buildBugInfo(iterator.next(), fix214);
                    sink.invoke(bug);
                  }
                } catch (HttpCancelledException e) {
                  throw new CancelledException(e);
                } catch (InterruptedException e) {
                  throw new RuntimeInterruptedException(e);
                }
                return null;
              }
            });
          } catch (CannotParseException e) {
            Log.warn("portion of requested bugs(" + count + ") are not loaded", e);
            continue;
          } catch (ConnectorException e) {
            if (progress != null) {
              progress.addError(e.getShortDescription());
            }
            throw e;
          }
        } finally {
          try {
            if (progress != null) {
              progress.setActivity(count);
              progress.setDone();
              progress = null; // help gc?
            }
          } catch (Exception e) {
            // ignore
          }
        }
      }
    } catch (HttpCancelledException e) {
      throw new CancelledException();
    }
  }

  private void pauseForMinimumDelay() throws InterruptedException, HttpCancelledException {
    if (BugzillaHTMLConstants.PAGELOAD_MINIMUM_DELAY <= 0)
      return;
    if (myLastPageLoad > 0) {
      long allowedTime = myLastPageLoad + BugzillaHTMLConstants.PAGELOAD_MINIMUM_DELAY * 1000;
      long wait = allowedTime - System.currentTimeMillis();
      while (wait > 10 && wait <= 60000) {
        Thread.sleep(Math.min(wait, 250));
        myMaterial.checkCancelled();
        wait = allowedTime - System.currentTimeMillis();
      }
    }
    myLastPageLoad = System.currentTimeMillis();
  }

  private boolean isBugzilla214(Document document) {
    String version =
      JDOMUtils.getAttributeValue(document.getRootElement(), BugzillaHTMLConstants.XML_TAG_VERSION, null, true);
    boolean fix214 = (version != null && version.startsWith("2.14"));
    return fix214;
  }

  private BugInfo buildBugInfo(Element element, boolean fix214) {
    BugInfo bug = new BugInfo(myServerInfo.getDefaultTimezone());
    String error = JDOMUtils.getAttributeValue(element, BugzillaHTMLConstants.XML_ATTRIBUTE_BUG_ERROR, null, true);
    if (error != null && error.length() > 0) {
      BugInfo.ErrorType errorType = BugzillaHTMLConstants.getErrorType(error);
      if (errorType != null)
        bug.setError(errorType);
      else
        Log.warn("unknown error attribute " + error);
    }
    List<Element> list = element.getChildren();
    for (Iterator<Element> it = list.iterator(); it.hasNext();) {
      Element tag = it.next();
      if (tryLoadAttribute(tag, bug, fix214))
        continue;
      if (tryLoadComment(tag, bug, fix214))
        continue;
      if (tryLoadAttachmentInfo(tag, bug, fix214))
        continue;
      if (tryLoadCustomField(tag, bug))
        continue;
      if (tryLoadFlag(tag, bug))
        continue;
      // not processing tokens at this time
      if ("token".equals(tag.getName()))
        continue;
      if (tag.getContentSize() == 0)
        Log.debug("skipping empty unknown tag " + tag);
      else
        Log.warn("cannot understand " + tag + " received for " + bug);
    }
    BugzillaValues values = bug.getValues();
    assert values.contains(BugzillaAttribute.ID) : bug; // even for errors

    String votes = values.getScalarValue(BugzillaAttribute.TOTAL_VOTES, null);
    if (votes == null) {
      // fix votes
      values.put(BugzillaAttribute.TOTAL_VOTES, "0");
    }

    if (!myAttachmentsExcluded)
      bug.setAttachmentsLoaded();

    return bug;
  }

  private boolean tryLoadCustomField(Element tag, BugInfo bug) {
    String fieldId = tag.getName();
    if (!fieldId.startsWith("cf_"))
      return false;
    String fieldValue = JDOMUtils.getText(tag);
    bug.addCustomFieldSingleValue(fieldId, fieldValue);
    return true;
  }

  private boolean tryLoadAttachmentInfo(Element tag, BugInfo bug, boolean fix214) {
    if (!BugzillaHTMLConstants.XML_TAG_BUG_ATTACHMENT.equals(tag.getName()))
      return false;
    String id = extract(tag, BugzillaHTMLConstants.XML_TAG_BUG_ATTACHMENT_ID, fix214);
    String date = extract(tag, BugzillaHTMLConstants.XML_TAG_BUG_ATTACHMENT_DATE, fix214);
    String description = extract(tag, BugzillaHTMLConstants.XML_TAG_BUG_ATTACHMENT_DESCRIPTION, fix214);
    Boolean obsolete = getBooleanAttribute(tag, "isobsolete");
    Boolean patch = getBooleanAttribute(tag, "ispatch");
    Boolean isprivate = getBooleanAttribute(tag, "isprivate");
    String filename = getChildTextOrNull(tag, "filename");
    String mimetype = getChildTextOrNull(tag, "type");
    List<BugInfo.Flag> flags = loadFlags(tag);
    long sz = Util.toLong(getChildTextOrNull(tag, "size"), -1);
    Long size = sz >= 0 ? (Long) sz : null;
    byte[] data = null;
    Element dataElement = JDOMUtils.getChild(tag, "data");
    if (dataElement != null) {
      if ("base64".equals(JDOMUtils.getAttributeValue(dataElement, "encoding", null, false))) {
        String encoded = JDOMUtils.getText(dataElement);
        byte[] source = encoded.getBytes();
        try {
          data = Base64.decodeBase64(source);
        } catch (Exception e) {
          Log.debug("cannot decode attachment");
        }
      }
    }
    bug.addAttachment(
      new BugInfo.Attachment(id, date, description, obsolete, patch, isprivate, filename, mimetype, size, data, flags));
    return true;
  }

  private boolean tryLoadFlag(Element tag, BugInfo bug) {
    if (!BugzillaHTMLConstants.XML_TAG_FLAG.equals(tag.getName())) return false;
    BugInfo.Flag flag = loadFlag(tag);
    if (flag != null) {
      bug.addFlag(flag);
      return true;
    }
    return false;
  }

  private List<BugInfo.Flag> loadFlags(Element bugOrAttachment) {
    List<Element> flags = bugOrAttachment.getChildren(BugzillaHTMLConstants.XML_TAG_FLAG);
    if (flags.isEmpty()) return Collections.emptyList();
    List<BugInfo.Flag> result = Collections15.arrayList();
    for (Element flag : flags) {
      BugInfo.Flag loadedFlag = loadFlag(flag);
      if (loadedFlag != null) result.add(loadedFlag);
    }
    return result;
  }

  @Nullable
  private BugInfo.Flag loadFlag(Element flag) {
    int id = getPositiveIntAttribute(flag, "id");
    int typeId = getPositiveIntAttribute(flag, "type_id");
    String strStatus = JDOMUtils.getAttributeValue(flag, "status", null, false);
    char status = BugInfo.Flag.parseStatus(strStatus);
    if (status == 0) return null;
    String name = JDOMUtils.getAttributeValue(flag, "name", null, false);
    if (name == null) {
      Log.error("<null> flag name. skipped");
      return null;
    }
    String setter = JDOMUtils.getAttributeValue(flag, "setter", null, false);
    if (setter == null) {
      Log.error("<null> flag setter. skipped");
    }
    String requestee = JDOMUtils.getAttributeValue(flag, "requestee", null, false);
    return new BugInfo.Flag(id, typeId, name, status, createFlagUser(setter),
      createFlagUser(requestee));
  }

  private BugzillaUser createFlagUser(String user) {
    if (myServerInfo.versionAtLeast(BZ_VER_FLAG_USER_LONG)) return BugzillaUser.longEmailName(user, null);
    String emailSuffix = myServerInfo.getEmailSuffix();
    return BugzillaUser.shortEmailName(user, null, emailSuffix);
  }

  private String getChildTextOrNull(Element tag, String tagName) {
    Element child = JDOMUtils.getChild(tag, tagName);
    return child == null ? null : JDOMUtils.getTextTrim(child);
  }

  private static Boolean getBooleanAttribute(Element tag, String attributeName) {
    String v = JDOMUtils.getAttributeValue(tag, attributeName, null, false);
    return v == null ? null : "1".equals(v);
  }

  /**
   * @return returns -1 if not attribute value available
   */
  private static int getPositiveIntAttribute(Element tag, String attributeName) {
    String value = JDOMUtils.getAttributeValue(tag, attributeName, null, false);
    if (value == null) return -1;
    try {
      int iValue = Integer.parseInt(value);
      if (iValue < 0) {
        Log.error(tag.getName() + "\\" + attributeName + "=" + value, new Throwable());
        return -1;
      }
      return iValue;
    } catch (NumberFormatException e) {
      Log.error(e);
      return -1;
    }
  }

  private String extract(Element bug, String tagName, boolean fix214) {
    String value = Util.NN(bug.getChildTextTrim(tagName));
    return OperUtils.replaceXmlEntities(value, true, fix214);
  }

  private BugzillaUser extractUser(Element bug, String tagName, boolean fix214) {
    String email = bug.getChildTextTrim(tagName);
    email = OperUtils.replaceXmlEntities(email, true, fix214);
    Element who = bug.getChild(tagName);
    String name;
    name = who != null ? who.getAttributeValue("name") : null;
    return BugzillaUser.longEmailName(email, name);
  }

  private boolean tryLoadComment(Element tag, BugInfo bug, boolean fix214) {
    if (!BugzillaHTMLConstants.XML_TAG_BUG_COMMENT.equals(tag.getName()))
      return false;
    BugzillaUser who = extractUser(tag, BugzillaHTMLConstants.XML_TAG_BUG_COMMENT_WHO, fix214);
    String when = extract(tag, BugzillaHTMLConstants.XML_TAG_BUG_COMMENT_WHEN, fix214);
    String text = extract(tag, BugzillaHTMLConstants.XML_TAG_BUG_COMMENT_TEXT, fix214);
    BigDecimal workTime = null;
    if (!fix214) {
      String tv = tag.getChildTextTrim(BugzillaHTMLConstants.XML_TAG_BUG_COMMENT_WORK_TIME);
      if (tv != null) {
        try {
          workTime = new BigDecimal(tv.trim());
        } catch (NumberFormatException e) {
          Log.debug(tv, e);
        }
      }
    }
    // support for old bugzilla feature (mime64) - see http://bugzilla.ximian.com
    String encoding = JDOMUtils.getAttributeValue(tag, "encoding", null, false);
    if ("base64".equals(encoding) && text.trim().length() > 0)
      text = decodeComment(text, encoding);                           

    Boolean isPrivate = null;
    String ps = JDOMUtils.getAttributeValue(tag, BugzillaHTMLConstants.XML_TAG_BUG_COMMENT_ISPRIVATE, null, false);
    if (ps != null) {
      isPrivate = "1".equals(ps);
    }

    bug.addComment(new Comment(who, when, text, myServerInfo.getDefaultTimezone(), isPrivate, workTime));
    return true;
  }


  private String decodeComment(String text, String encoding) {
    byte[] decoded = null;
    try {
      decoded = Base64.decodeBase64(text.getBytes());
    } catch (Exception e) {
      // ignore
      Log.warn(encoding, e);
    }
    if (decoded != null && decoded.length > 0) {
      String newText = null;
      String charset = myMaterial.getCharset();
      if (charset != null) {
        try {
          newText = new String(decoded, charset);
        } catch (Exception e) {
          // ignore
        }
      }
      if (newText == null) {
        try {
          newText = new String(decoded, HttpLoader.DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
          // ignore
        }
      }
      if (newText != null)
        text = newText;
    }
    return text;
  }

  private boolean tryLoadAttribute(Element tag, BugInfo bug, boolean fix214) {
    BugzillaAttribute attribute = BugzillaHTMLConstants.XML_TAG_TO_ATTRIBUTE_MAP.get(tag.getName());
    if (attribute == null)
      return false;
    String value = JDOMUtils.getTextTrim(tag);
    if (fix214)
      value = OperUtils.replaceXmlEntities(value, true, fix214);
    value = convertValue(attribute, value);
    String[] values = tryGetValueArray(attribute, value);
    values = correctUsers(attribute, values);
    value = correctUsers(attribute, value);
    BugzillaValues vv = bug.getValues();
    if (values != null) {
      for (String v : values)
        vv.put(attribute, v);
    } else if (value != null) {
      vv.put(attribute, value);
    }
    return true;
  }

  private String[] correctUsers(BugzillaAttribute attribute, String[] values) {
    if (values == null || values.length == 0) return values;
    if (!shortUserEmail(attribute)) return values;
    String suffix = myServerInfo.getEmailSuffix();
    if (suffix == null || suffix.length() == 0) return values;
    String[] result = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      result[i] = value != null && value.length() > 0 ? value.trim() + suffix : value;
    }
    return result;
  }

  private String correctUsers(BugzillaAttribute attribute, String value) {
    if (value == null || value.length() == 0) return value;
    if (!shortUserEmail(attribute)) return value;
    String suffix = myServerInfo.getEmailSuffix();
    if (suffix == null || suffix.length() == 0) return value;
    return value.trim() + suffix;
  }

  private boolean shortUserEmail(BugzillaAttribute attribute) {
    return BugzillaAttribute.CC.equals(attribute);
  }

  private String[] tryGetValueArray(BugzillaAttribute attribute, String value) {
    if (value == null)
      return null;
    if (attribute == BugzillaAttribute.BLOCKED_BY || attribute == BugzillaAttribute.BLOCKS ||
      attribute == BugzillaAttribute.CC)
    {
      if (value.indexOf(',') >= 0) {
        String[] strings = value.split("\\s*,\\s*");
        for (int i = 0; i < strings.length; i++) {
          if (strings[i] == null || strings[i].trim().length() == 0) {
            // remove empty
            List<String> r = Collections15.arrayList(strings);
            for (Iterator<String> ii = r.iterator(); ii.hasNext();) {
              String s = ii.next();
              if (s == null || s.trim().length() == 0)
                ii.remove();
            }
            return r.toArray(new String[r.size()]);
          }
        }
        return strings;
      }
    }
    return null;
  }

  private String convertValue(BugzillaAttribute attribute, String value) {
    if (attribute == BugzillaAttribute.ASSIGNED_TO || attribute == BugzillaAttribute.REPORTER ||
      attribute == BugzillaAttribute.QA_CONTACT)
    {

      if ("__UNKNOWN__".equals(value))
        return null;
    }

    if (attribute == BugzillaAttribute.BLOCKED_BY || attribute == BugzillaAttribute.BLOCKS) {
      if (value != null && value.length() == 0)
        return null;
    }

    return value;
  }

  private Pair<String, Integer> buildNextURL() {
    if (myNextIDIndex >= myIDs.length)
      return null;
    long strategy = myServerInfo.getStateStorage().getPersistentLong(LOAD_STRATEGY);
    if (myServerInfo.versionAtLeast(BZ_VER_NO_XML_CGI)) strategy = 1;
    StringBuffer result = new StringBuffer(myServerInfo.getBaseURL());
    if (strategy == 1) {
      result.append(BugzillaHTMLConstants.BZ_2_18.URL_BUGS_LOADXML_PREFIX);
    } else {
      result.append(BugzillaHTMLConstants.URL_BUGS_LOADXML_PREFIX);
    }
    int count = 0;
    while (myNextIDIndex < myIDs.length && count < BugzillaHTMLConstants.BUG_FIELDS_URL_MAX_IDS) {
      if (strategy == 1) {
        result.append("&id=").append(myIDs[myNextIDIndex]);
      } else {
        if (count > 0) {
          result.append("%2C");
        }
        result.append(myIDs[myNextIDIndex]);
      }
      myNextIDIndex++;
      count++;
    }
    if (myExtraParams != null && myExtraParams.length() > 0)
      result.append('&').append(myExtraParams);
    String url = result.toString();
    if (myForTest) {
      url = myServerInfo.getBaseURL();
    }
    return Pair.create(url, count);
  }

  private static class BugTracker implements StringTransferTracker {
    private static final String LOOK_FOR = "</" + BugzillaHTMLConstants.XML_TAG_BUG + ">";
    private final Progress myProgress;
    private final float myIncrement;

    private long myNextCountPossible = 0;

    public BugTracker(Progress sink, int expectedTotal) {
      myProgress = sink;
      myIncrement = expectedTotal < 1 ? 0.01F : 1F / expectedTotal;
    }

    public void onTransfer(StringBuilder buffer) {
      int count = 0;
      int k = 0;
      long start = System.currentTimeMillis();
      if (start < myNextCountPossible) {
        // too fast
        return;
      }
      while (true) {
        k = buffer.indexOf(LOOK_FOR, k);
        if (k < 0)
          break;
        count++;
        k++;
      }
      long now = System.currentTimeMillis();
      long spent = now - start;
      // take less than 1% cpu time, but count not rarely than once in 5 sec
      myNextCountPossible = now + Math.min(spent * 100, 5000);

      float progress = Math.min(myIncrement * count, 1F);
      myProgress.setProgress(progress, count);
    }

    public void setContentLengthHint(long length) {
    }
  }

//  private static class MyActivityProducer extends Convertor<List<ProgressSource>, PLoadBugs> {
//    private final int myTotalCount;
//
//    public MyActivityProducer(int totalCount) {
//      myTotalCount = totalCount;
//    }
//
//    public PLoadBugs convert(List<ProgressSource> progressSources) {
//      int z = progressSources.size();
//      if (z == 0)
//        return null;
//      int count = 0;
//      boolean waitingReply = false;
//      for (int i = 0; i < z; i++) {
//        ProgressSource source = progressSources.get(i);
//        Object activity = source.getProgress().getActivity();
//        if (activity instanceof Integer) {
//          int v = ((Integer) activity).intValue();
//          if (v >= 0)
//            count += v;
//          else
//            waitingReply = true;
//        }
//      }
//      if (count == 0)
//        return PLoadBugs.waiting();
//      else
//        return PLoadBugs.bugsDownloaded(count, myTotalCount, waitingReply);
//    }
//  }
}
