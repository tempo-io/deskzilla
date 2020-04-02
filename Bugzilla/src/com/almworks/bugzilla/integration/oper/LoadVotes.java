package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.util.Pair;
import com.almworks.util.RunnableRE;
import com.almworks.util.commons.Condition;
import com.almworks.util.text.TextUtil;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// review(IS,2008-04-28): review later
public class LoadVotes extends BugzillaOperation {
  private final ServerInfo myServerInfo;
  @Nullable private final String myId;
  private static final Pattern ONLY_S_D = Pattern.compile("only\\s+\\d+");

  public LoadVotes(ServerInfo serverInfo, @Nullable String id) {
    super(serverInfo);
    myServerInfo = serverInfo;
    myId = id;
  }

  @Nullable

  public UserVoteInfo loadMyVotes() throws ConnectorException {
    return runOperation(new MyVotesLoader());
  }

  @NotNull
  public List<Pair<BugzillaUser, Integer>> loadBugVotes() throws ConnectorException {
    return runOperation(new BugVotesLoader());
  }


  private class MyVotesLoader implements RunnableRE<UserVoteInfo, ConnectorException> {
    public UserVoteInfo run() throws ConnectorException {
      DocumentLoader loader = getDocumentLoader(
        myId != null
          ? myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_USER_VOTES + myId
          : myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_VOTE_POST,
        true);

      Document document = loader.httpGET().loadHTML();
      BugzillaErrorDetector.detectAndThrow(document, "loadVotes");
      
      UserVoteInfo result = new UserVoteInfo();
      Map<String, VoteProductInfo> resultMap = Collections15.hashMap();
      result.myInfo = resultMap;

      Element form = findVotingForm(document.getRootElement());
      if (form != null) {
        Element table = JDOMUtils.searchElement(form, "table");
        if (table != null) {
          List<Element> trList = JDOMUtils.getChildren(table, "tr");
          VoteProductInfo productInfo = null;
          if (trList != null && trList.size() > 1) {
            for (int i = 1; i < trList.size(); i++) {
              Element trElement = trList.get(i);
              Element prodElement = JDOMUtils.searchElement(trElement, "th");
              productInfo = handleLine(result, resultMap, productInfo, trElement, prodElement);
            }
          } else {
            Log.warn("table not found during myVote loading");
            return null;
          }
        }
      }

      return result;
    }

    private Element findVotingForm(Element root) {
      final Iterator<Element> it = JDOMUtils.searchElementIterator2(
        root, "form", "action", new Condition<String>() {
          @Override
          public boolean isAccepted(String value) {
            return BugzillaHTMLConstants.isVoteAction(value);
          }
        });
      return it.hasNext() ? it.next() : null;
    }

    private VoteProductInfo handleLine(UserVoteInfo result, Map<String, VoteProductInfo> resultMap,
      VoteProductInfo productInfo, Element trElement, Element prodElement)
    {
      if (prodElement != null) {
        productInfo = handleNewProductLine(resultMap, trElement, prodElement);
      } else {
        assert productInfo != null;
        if (JDOMUtils.searchElement(trElement, "input") != null && productInfo != null) {
          handleInputLine(result, productInfo, trElement);
        } else {
          handleUsedInProductLine(productInfo, trElement);
        }
      }
      return productInfo;
    }

    private void handleUsedInProductLine(VoteProductInfo productInfo, Element trElement) {
      Element usedFrom = JDOMUtils.searchElement(trElement, "td", "colspan", "3");
      if (usedFrom != null) {
        String[] items = JDOMUtils.getTextTrim(usedFrom).split("\\s+");
        try {
          productInfo.myUsed = Integer.parseInt(items[0]);
          productInfo.myTotal = Integer.parseInt(items[5]);
        } catch (RuntimeException e) {
          Log.warn("cannot get total and used vote count " + items, e);
        }
      } else {
        Log.warn("last element with colspan 3 not found (used and total votes)");
      }
    }

    private void handleInputLine(UserVoteInfo result, @NotNull VoteProductInfo productInfo, Element trElement) {
      Element vote = JDOMUtils.searchElement(trElement, "input");
      String id = JDOMUtils.getAttributeValue(vote, "name", "", true);
      String myVotes = JDOMUtils.getAttributeValue(vote, "value", "", true);
      try {
        productInfo.myVotes.add(Pair.create(id, Integer.parseInt(myVotes)));
        if (myId != null && id.equals(String.valueOf(myId))) {
          result.myIdFound = true;
        }
      } catch (NumberFormatException e) {
        Log.warn(e);
      }
    }

    private VoteProductInfo handleNewProductLine(Map<String, VoteProductInfo> resultMap, Element trElement,
      Element prodElement)
    {
      String product = prodElement != null ? JDOMUtils.getTextTrim(prodElement) : null;
      VoteProductInfo productInfo = new VoteProductInfo();
      productInfo.myVotes = Collections15.arrayList();
      Element text = JDOMUtils.searchElement(trElement, "font");
      if (text != null) {
        String t = JDOMUtils.getTextTrim(text);
        Matcher matcher = ONLY_S_D.matcher(t);
        if (matcher.find()) {
          String matched = t.substring(matcher.start(), matcher.end());
          int i = TextUtil.findFirstNonNegativeInt(matched, -1);
          if (i > 0) {
            productInfo.myVotePerBug = i;
          }
        }
      }
      resultMap.put(product, productInfo);
      return productInfo;
    }
  }


  private class BugVotesLoader implements RunnableRE<List<Pair<BugzillaUser, Integer>>, ConnectorException> {
    public List<Pair<BugzillaUser, Integer>> run() throws ConnectorException {
      assert myId != null;
      DocumentLoader loader = getDocumentLoader(myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_TOTAL_BUG_VOTES + myId, true);
      Document document = loader.httpGET().loadHTML();
      Iterator<Element> elementIterator = JDOMUtils.searchElementIterator(document.getRootElement(), "table");
      return extractDataFromFirstNonEmptyTableOrNull(elementIterator);
    }

    private List<Pair<BugzillaUser, Integer>> extractDataFromFirstNonEmptyTableOrNull(Iterator<Element> elementIterator) {
      while (elementIterator.hasNext()) {
        Element table = elementIterator.next();
        List<Element> lines = JDOMUtils.getChildren(table, "tr");
        if (lines != null && lines.size() > 0) {
          List<Element> header = JDOMUtils.getChildren(lines.get(0), "th");
          if (header != null && header.size() > 0 && JDOMUtils.getTextTrim(header.get(0)).equalsIgnoreCase("who")) {
            return extractVotePairs(lines);
          }
        }
      }
      return null;
    }

    private List<Pair<BugzillaUser, Integer>> extractVotePairs(List<Element> lines) {
      List<Pair<BugzillaUser, Integer>> result = Collections15.arrayList();
      for (int i = 1; i < lines.size(); i++) {
        Element line = lines.get(i);
        List<Element> td = JDOMUtils.getChildren(line, "td");
        if (td != null && td.size() >= 2) {
          Element whoTD = td.get(0);
          Element whoHref = JDOMUtils.getChild(whoTD, "a");
          if (whoHref != null) {
            BugzillaUser mail = BugzillaUser.shortEmailName(JDOMUtils.getTextTrim(whoHref), null, myServerInfo.getEmailSuffix());
            try {
              Element countTD = td.get(1);
              int count = Integer.parseInt(JDOMUtils.getTextTrim(countTD));
              result.add(Pair.create(mail, count));
            } catch (NumberFormatException e) {
              Log.warn(e);
            }
          }
        }
      }
      return result;
    }
  }
}
