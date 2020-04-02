package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.CannotParseException;
import com.almworks.api.connector.http.HtmlUtils;
import com.almworks.api.http.HttpUtils;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.oper.js.JSTokenizer;
import com.almworks.bugzilla.integration.oper.js.JSTokenizerAdapter;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.commons.Condition;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Document;
import org.jdom.Element;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadFrontPage extends BugzillaOperation {
  private final String myUrl;
  private final Object myId;
  private static final Pattern NUMBER_IN_PARENTH = Pattern.compile("\\((\\d+)\\)");
  private final ServerInfo myServerInfo;
//  private final SimpleDateFormat myTimestampFormat = new SimpleDateFormat("yyyyMMddhhmmss");

  public LoadFrontPage(ServerInfo info, String url, Object id) {
    super(info);
    myServerInfo = info;
    assert url != null;
    myUrl = url;
    myId = id;
  }

  public FrontPageData loadFrontPage() throws ConnectorException {
    return runOperation(new RunnableRE<FrontPageData, ConnectorException>() {
      public FrontPageData run() throws ConnectorException {
        clearBuglistCookie();
        Document document = getDocumentLoader(myUrl, true).httpGET().loadHTML();
        try {
          String deltaTs = findDeltaTs(document);
          MultiMap<String, String> parameters;
          List<Pair<BugGroupData, Boolean>> groups;
          List<FrontPageData.CommentInfo> commentInfo = null;
          Map<String, CustomFieldInfo> customFieldInfo = null;
          MultiMap<String, String> customFieldValues = null;
          List<String> knobs;
          List<StatusInfo> allowedStatusChanges = null;
          String markDuplicateStatus = null;
          int commentPrivacyFormat = 0;
          boolean seeAlsoSeen = false;
          List<String> currentSeeAlso = null;
          CustomFieldDependencies customFieldDependencies = null;
          List<FrontPageData.FlagInfo> flags = null;

          Element form = OperUtils.findUpdateFormElement(document.getRootElement(), false);
          if (form == null) {
            Log.warn("cannot find bug update form [" + myUrl + "]");
            parameters = MultiMap.create();
            groups = Collections15.emptyList();
            knobs = Collections15.emptyList();
          } else {
            parameters = HtmlUtils.extractDefaultFormParameters(form);
            groups = OperUtils.findGroupInfo(form);
            boolean[] format32 = {false};
            commentInfo = OperUtils.findCommentInfo(form, myServerInfo.getDefaultTimezone(), format32);
            if (format32[0])
              commentPrivacyFormat = FrontPageData.COMMENT_PRIVACY_FORMAT_BUGZILLA_32;
            Pair<Map<String, CustomFieldInfo>, MultiMap<String, String>> pair =
              OperUtils.findCustomFieldInfoAndValues(form, false);
            if (pair != null) {
              customFieldInfo = pair.getFirst();
              customFieldValues = pair.getSecond();
            }
            knobs = OperUtils.findKnobs(form);
            if (knobs.isEmpty()) {
              // bz3.2
              Pair<List<StatusInfo>, String> pair2 = findAllowedStatusChanges(form);
              allowedStatusChanges = pair2 == null ? null : pair2.getFirst();
              if (allowedStatusChanges != null) {
                markDuplicateStatus = pair2.getSecond();
              }
            }
            seeAlsoSeen = JDOMUtils.searchElement(form, "input", "name", "see_also") != null;
            if (seeAlsoSeen) {
              currentSeeAlso = Collections15.arrayList();
              Iterator<Element> ii = JDOMUtils.searchElementIterator(form, "input", "name", "remove_see_also");
              while (ii.hasNext()) {
                Element e = ii.next();
                if (!"checkbox".equalsIgnoreCase(JDOMUtils.getAttributeValue(e, "type", "", false)))
                  continue;
                String v = JDOMUtils.getAttributeValue(e, "value", "", true);
                if (v.length() > 0)
                  currentSeeAlso.add(v);
              }
            }
            customFieldDependencies = CustomFieldDependencyExtractor.getDependencies(
              document.getRootElement(), CustomFieldDependencies.Source.EXISTING_BUG);
            flags = FlagsExtractor.extractFlagInfo(form, myServerInfo.getEmailSuffix());
          }
          Integer votes = findVotesCount(document.getRootElement());
          boolean voteLink = findVoteLink(document.getRootElement());
          return new FrontPageData(deltaTs, parameters, groups, commentInfo, customFieldInfo, customFieldValues, knobs,
            allowedStatusChanges, markDuplicateStatus, votes, commentPrivacyFormat, seeAlsoSeen, currentSeeAlso,
            customFieldDependencies, flags, voteLink);
        } catch (CannotParseException e) {
          BugzillaErrorDetector.detectAndThrow(document, "loading bug page");
          throw e;
        }
      }
    });
  }

  private Integer findVotesCount(Element root) {
    Integer r = findVotesCountInLink(root);
    if (r != null)
      return r;
    return findVotesCountInSpan(root);
  }

  private Integer findVotesCountInSpan(Element root) {
    Element votes = JDOMUtils.searchElement(root, "span", "id", "votes_container");
    if (votes == null)
      return null;
    Iterator<Element> ii = JDOMUtils.searchElementIterator(votes, "a");
    while (ii.hasNext()) {
      Element a = ii.next();
      String text = JDOMUtils.getTextTrim(a);
      int k = 0;
      while (k < text.length() && Character.isDigit(text.charAt(k)))
        k++;
      if (k > 0) {
        try {
          int v = Integer.parseInt(text.substring(0, k));
          return v;
        } catch (NumberFormatException e) {
          continue;
        }
      }
    }
    return 0;
  }

  private Integer findVotesCountInLink(Element root) {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(root, "link");
    while (ii.hasNext()) {
      Element link = ii.next();
      String href = JDOMUtils.getAttributeValue(link, "href", null, true);
      if (href == null || !href.contains("votes.cgi") || !href.contains("bug_id=" + myId))
        continue;
      String title = JDOMUtils.getAttributeValue(link, "title", null, true);
      if (title == null)
        continue;
      Matcher matcher = NUMBER_IN_PARENTH.matcher(title);
      if (matcher.find()) {
        try {
          int v = Integer.parseInt(matcher.group(1));
          return v;
        } catch (NumberFormatException e) {
          // ignore
        }
      }
    }
    return null;
  }

  private boolean findVoteLink(Element root) {
    return JDOMUtils.searchElementIterator2(
      root, "a", "href", new Condition<String>() {
        @Override
        public boolean isAccepted(String href) {
          return BugzillaHTMLConstants.isVoteAction(href) && href.contains("bug_id=" + myId);
        }
      }).hasNext();
  }

  static Pair<List<StatusInfo>, String> findAllowedStatusChanges(Element updateForm) {
    List<String> options = null;
    Element select = JDOMUtils.searchElement(updateForm, "select", "name", "bug_status");
    if (select != null) {
      options = HtmlUtils.getSelectOptionValues(select);
    } else {
      Element input = JDOMUtils.searchElement(updateForm, "input", "name", "bug_status");
      if (input != null) {
        if ("hidden".equalsIgnoreCase(JDOMUtils.getAttributeValue(input, "type", "", false))) {
          String status = JDOMUtils.getAttributeValue(input, "value", "", true);
          options = Collections.singletonList(status);
        } else {
          Log.warn("strange input " + input);
        }
      }
    }
    if (options == null) {
      return null;
    }
    final List<String> closedStates = Collections15.arrayList();
    StringBuilder duplicateStatus = new StringBuilder();
    boolean foundJS = getStatusInfoFromJS(updateForm, closedStates, duplicateStatus);
    List<StatusInfo> r = Collections15.arrayList(options.size());
    for (String option : options) {
      r.add(new StatusInfo(option, foundJS ? !closedStates.contains(option) : null));
    }
    String dupStatus = duplicateStatus.toString();
    if (dupStatus.length() == 0 || !options.contains(dupStatus))
      dupStatus = null;
    return Pair.create(r, dupStatus);
  }

  private static boolean getStatusInfoFromJS(Element updateForm, List<String> closedStates,
    StringBuilder duplicateStatus)
  {
    Iterator<Element> ii =
      JDOMUtils.searchElementIterator(updateForm.getDocument().getRootElement(), "script", "type", "text/javascript");
    while (ii.hasNext()) {
      Element script = ii.next();
      String text = JDOMUtils.getText(script, true);
      if (!text.contains("close_status_array"))
        continue;
      try {
        ClosedStateFinder finder = new ClosedStateFinder(closedStates, duplicateStatus);
        new JSTokenizer(text).visit(finder);
        return finder.foundStatus;
      } catch (ParseException e) {
        Log.warn("cannot parse javascript [" + script + "]");
        continue;
      }
    }
    return false;
  }

  private void clearBuglistCookie() {
    HttpUtils.removeCookies(myMaterial.getHttpClient().getState(), BugzillaHTMLConstants.BUGLIST_COOKIE_NAME);
  }

  private String findDeltaTs(Document document) throws CannotParseException {
    Element element = JDOMUtils.searchElement(document.getRootElement(), "input", "name", "delta_ts");
    if (element == null)
      throw new CannotParseException(myUrl, "cannot find delta_ts input");
    String delta_ts = JDOMUtils.getAttributeValue(element, "value", "", true);
    return delta_ts;
  }

  private static class ClosedStateFinder extends JSTokenizerAdapter {
    private int step = 0;
    private final List<String> myClosedStates;
    private final StringBuilder myDuplicateStatus;

    boolean foundStatus = false;

    public ClosedStateFinder(List<String> closedStates, StringBuilder duplicateStatus) {
      myClosedStates = closedStates;
      myDuplicateStatus = duplicateStatus;
    }

    @Override
    public void visitIdentifier(String identifier) {
      switch (step) {
      case 0:
        step = "var".equalsIgnoreCase(identifier) ? 1 : 0;
        break;
      case 1:
        step = "close_status_array".equalsIgnoreCase(identifier) ? 2 : 0;
        break;
      case 3:
        step = "new".equalsIgnoreCase(identifier) ? 4 : 0;
        break;
      case 4:
        step = "Array".equalsIgnoreCase(identifier) ? 5 : 0;
        break;
      case 11:
        step++;
        break;
      default:
        step = 0;
      }
    }

    @Override
    public void visitSpecialChar(char c) {
      switch (step) {
      case 2:
        step = c == '=' ? 3 : 0;
        break;
      case 3:
        step = c == '[' ? 6 : 0;
        foundStatus = true;
        break;
      case 5:
        step = c == '(' ? 6 : 0;
        foundStatus = true;
        break;
      case 7:
        step = c == ',' ? 6 : 0;
        break;
      case 8:
      case 10:
      case 12:
        step = c == ',' ? step + 1 : 0;
        break;
      default:
        step = 0;
      }
    }

    @Override
    public void visitStringLiteral(String literal) {
      switch (step) {
      case 6:
        myClosedStates.add(literal);
        step = 7;
        break;
      case 0:
        step = "dup_id_discoverable_action".equalsIgnoreCase(literal) ? 8 : 0;
        break;
      case 9:
        step++;
        break;
      case 13:
        myDuplicateStatus.setLength(0);
        myDuplicateStatus.append(literal);
        step = 0;
        break;
      default:
        step = 0;
      }
    }
  }
}
