package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.CannotParseException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.BugzillaUser;
import com.almworks.bugzilla.integration.data.ChangeSet;
import com.almworks.util.RunnableRE;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.jdom.Document;
import org.jdom.Element;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class LoadActivity extends BugzillaOperation {
  private static final long SECOND = 1000;
  private final String myUrl;
  private ChangeSet myLastChangeSet;

  private Element myActivityTable;
  private List<Integer> myContinuationIndices;
  private String[] myLastRowData;
  private boolean myContinuationMode;
  private int myContinuationLines;
  private int myAddedIndex;
  private int myRemovedIndex;
  private int myWhatIndex;
  private int myWhenIndex;
  private int myWhoIndex;
  private final ServerInfo myServerInfo;

  public LoadActivity(ServerInfo serverInfo, int bugId) {
    super(serverInfo);
    myServerInfo = serverInfo;
    myUrl = serverInfo.getBaseURL() + BugzillaHTMLConstants.URL_BUG_ACTIVITY + bugId;
  }

  public List<ChangeSet> loadActivity() throws ConnectorException {
    return runOperation(new RunnableRE<List<ChangeSet>, ConnectorException>() {
      public List<ChangeSet> run() throws ConnectorException {
        clearVariables();
        DocumentLoader loader = getDocumentLoader(myUrl, true);
        myMaterial.setLastServerResponseTime(0);
        Document document = loader.httpGET().loadHTML();
        // search for a changes table - it has a header with
        searchActivityTable(document);
        if (myActivityTable == null) {
          BugzillaErrorDetector.detectAndThrow(document, "loading bug activity");
          String message = "cannot find activity table, assuming no activity [" + myUrl + "]";
          Log.debug(message);
          loader.setApplicationMessage(message);
          return Collections15.emptyList();
        }
        if (!checkIndices()) {
          throw new CannotParseException(myUrl, "cannot understand activity table headers");
        }
        List<ChangeSet> result = parseActivityTable();
        if (result == null) {
          throw new CannotParseException(myUrl, "activity table parsing failed");
        }
        return result;
      }
    });
  }

  private void advanceChangeSet(List<ChangeSet> result, BugzillaUser who, Date when) {
    if (myLastChangeSet != null) {
      if (!Util.equals(myLastChangeSet.getWho(), who) ||
        Math.abs(when.getTime() - myLastChangeSet.getWhen().getTime()) >= SECOND) {

        result.add(myLastChangeSet);
        myLastChangeSet = null;
      }
    }
    if (myLastChangeSet == null)
      myLastChangeSet = new ChangeSet(who, when);
  }

  private boolean checkIndices() {
    int[] indices = {myWhatIndex, myWhenIndex, myWhoIndex, myRemovedIndex, myAddedIndex};
    Arrays.sort(indices);
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] != i)
        return false;
    }
    return true;
  }

  private void clearIndices() {
    myWhatIndex = -1;
    myWhoIndex = -1;
    myWhenIndex = -1;
    myAddedIndex = -1;
    myRemovedIndex = -1;
  }

  private void clearVariables() {
    clearIndices();
    myActivityTable = null;
    myContinuationIndices = null;
    myContinuationLines = 0;
    myContinuationMode = false;
    myLastChangeSet = null;
    myLastRowData = null;
  }

  private boolean compare(String header, String expectedHeader) {
    return String.CASE_INSENSITIVE_ORDER.compare(header, expectedHeader) == 0;
  }

  private int getInt(String s, int defaultValue) {
    if (s == null)
      return defaultValue;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private void initRowCycle() {
    myLastRowData = new String[]{"", "", "", "", ""};
    myContinuationMode = false;
    myContinuationLines = 0;
    myContinuationIndices = Collections15.arrayList(5);
    myLastChangeSet = null;
  }

  private boolean loadRowData(Element row, int rowCount) {
    Iterator<Element> cells = JDOMUtils.searchElementIterator(row, "td");
    if (!cells.hasNext())
      return false;

    if (myContinuationMode && myContinuationLines <= 0)
      myContinuationMode = false;
    if (myContinuationMode)
      myContinuationLines--;

    int count = 0;
    int rowspan = 0;
    if (!myContinuationMode)
      myContinuationIndices.clear();
    int maxIndex = myContinuationMode ? myContinuationIndices.size() : myLastRowData.length;
    while (cells.hasNext()) {
      int i = count++;
      if (i >= maxIndex) {
        Log.warn("row " + rowCount + " has too many columns");
        break;
      }
      Element cell = cells.next();
      int index = myContinuationMode ? myContinuationIndices.get(i).intValue() : i;
      myLastRowData[index] = JDOMUtils.getTextTrim(cell);
      if (!myContinuationMode) {
        int cellRowspan = getInt(JDOMUtils.getAttributeValue(cell, "rowspan", null, false), 1);
        if (cellRowspan == 1) {
          myContinuationIndices.add(i);
        } else {
          if (rowspan == 0) {
            rowspan = cellRowspan;
          } else {
            if (rowspan != cellRowspan) {
              Log.warn("rowspan do not coincide for row " + rowCount + ": " + cellRowspan + " != " + rowspan);
              break;
            }
          }
        }
      }
    }
    if (count != maxIndex) {
      Log.warn("activity row " + rowCount + " is not understood, too few cells");
      return false;
    }
    if (rowspan > 1) {
      myContinuationMode = true;
      myContinuationLines = rowspan - 1;
    }
    return true;
  }

  private List<ChangeSet> parseActivityTable() {
    // todo refactor long method
    List<ChangeSet> myResult = Collections15.arrayList();
    Iterator<Element> rows = JDOMUtils.searchElementIterator(myActivityTable, "tr");
    int rowCount = 0;
    initRowCycle();
    while (rows.hasNext()) {
      rowCount++;
      boolean r = loadRowData(rows.next(), rowCount);
      if (!r)
        continue;

      BugzillaUser who = BugzillaUser.shortEmailName(myLastRowData[myWhoIndex], null, myServerInfo.getEmailSuffix());
      Date when = BugzillaDateUtil.parseOrWarn(myLastRowData[myWhenIndex], myServerInfo.getDefaultTimezone());
      if (when == null || who == null)
        continue;

      advanceChangeSet(myResult, who, when);
      String what = myLastRowData[myWhatIndex];
      BugzillaAttribute attribute = BugzillaHTMLConstants.BUG_ACTIVITY_WHAT_ATTRIBUTE_MAP.get(what);
      if (attribute == null) {
        Log.warn("unrecognized field " + what + " in bug activity");
        continue;
      }
      myLastChangeSet.addChange(
        new ChangeSet.Change(attribute, myLastRowData[myRemovedIndex], myLastRowData[myAddedIndex]));
    }
    if (myLastChangeSet != null)
      myResult.add(myLastChangeSet);
    Collections.reverse(myResult);
    return myResult;
  }

  private void searchActivityTable(Document document) {
    myActivityTable = null;
    List<Element> tables = JDOMUtils.searchElements(document.getRootElement(), "table");
    for (Iterator<Element> ii = tables.iterator(); ii.hasNext();) {
      Element table = ii.next();
      Iterator<Element> jj = JDOMUtils.searchElementIterator(table, "tr");
      if (!jj.hasNext())
        continue;
      // take first row
      Element tr = jj.next();
      List<Element> ths = JDOMUtils.searchElements(tr, "th");
      clearIndices();
      int column = 0;
      Iterator<Element> kk = ths.iterator();
      while (kk.hasNext()) {
        Element element = kk.next();
        String header = JDOMUtils.getTextTrim(element);
        if (compare(header, BugzillaHTMLConstants.BUG_ACTIVITY_TABLE_HEADER_WHAT) && myWhatIndex < 0)
          myWhatIndex = column;
        else if (compare(header, BugzillaHTMLConstants.BUG_ACTIVITY_TABLE_HEADER_WHO) && myWhoIndex < 0)
          myWhoIndex = column;
        else if (compare(header, BugzillaHTMLConstants.BUG_ACTIVITY_TABLE_HEADER_WHEN) && myWhenIndex < 0)
          myWhenIndex = column;
        else if (compare(header, BugzillaHTMLConstants.BUG_ACTIVITY_TABLE_HEADER_REMOVED) && myRemovedIndex < 0)
          myRemovedIndex = column;
        else if (compare(header, BugzillaHTMLConstants.BUG_ACTIVITY_TABLE_HEADER_ADDED) && myAddedIndex < 0)
          myAddedIndex = column;
        else
          break;
        column++;
      }
      if (kk.hasNext() || column != 5)
        continue;
      myActivityTable = table;
      return;
    }
  }

  int[] testSearchActivityTable(Document document) {
    searchActivityTable(document);
    return new int[]{myWhoIndex, myWhenIndex, myWhatIndex, myRemovedIndex, myAddedIndex};
  }

  List<ChangeSet> testParseActivityTable(Element activityTable, int[] indices) {
    myWhoIndex = indices[0];
    myWhenIndex = indices[1];
    myWhatIndex = indices[2];
    myRemovedIndex = indices[3];
    myAddedIndex = indices[4];
    if (!checkIndices())
      throw new IllegalArgumentException("bad indices");
    myActivityTable = activityTable;
    return parseActivityTable();
  }
}
