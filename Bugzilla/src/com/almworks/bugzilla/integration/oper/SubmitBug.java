package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.connector.http.ExtractFormParameters;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.err.UploadException;
import com.almworks.util.*;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.progress.Progress;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.NameValuePair;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.almworks.bugzilla.integration.BugzillaAttribute.SHORT_DESCRIPTION;
import static com.almworks.bugzilla.integration.BugzillaAttribute.STATUS;
import static com.almworks.bugzilla.integration.QueryURL.Column.ID_DESC;
import static com.almworks.bugzilla.integration.data.BooleanChartElementType.EQUALS;

public class SubmitBug extends BugzillaOperation {
  private static final long SUBMIT_TIME_DIFF_TOLERANCE = Const.MINUTE * 5;
  private static final int MAX_CANDIDATES = 10;
  private static final TypedKey<Boolean> NO_SUBMIT_FORM = TypedKey.create("NSF");

  private static final Collection<String> BUG_ID_TAGS = StringUtil.caseInsensitiveSet("title", "p", "dt", "h1", "h2");
  private static final Pattern[] BUG_ID_PATTERNS = {
    Pattern.compile("bug\\s+(\\d+)\\s+submitted", Pattern.CASE_INSENSITIVE),
    Pattern.compile("bug\\s+(\\d+)\\s+has\\s+been\\s+added\\s+to\\s+the\\s+database", Pattern.CASE_INSENSITIVE),
    Pattern.compile("back\\s+to\\s+bug\\s+#\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
    Pattern.compile("bug\\s+(\\d+)\\s+posted", Pattern.CASE_INSENSITIVE),
  };

  private static final Collection<String> A_TAG = StringUtil.caseInsensitiveSet("a");
  private static final Pattern HREF_PATTERN =  Pattern.compile("show_activity\\.cgi\\?id=(\\d+)");

  private final String mySubmitURL;
  private final BugzillaIntegration myIntegration;
  private final String myUsername;
  private ServerInfo myServerInfo;

  public SubmitBug(ServerInfo info, BugzillaIntegration integration, String submitURL, String username) {
    super(info);
    myServerInfo = info;
    myIntegration = integration;
    myUsername = username;
    assert submitURL != null;
    assert info.getBaseURL() != null;
    mySubmitURL = submitURL;
  }

  public BugSubmitResult submit(BugInfoForUpload sourceInfo) throws ConnectorException {
    Integer newID = null;
    String submitKey = getSubmitKey(sourceInfo);
    BugInfoForUpload bugInfo = null;
    int postCount = 0;

    try {
      String product = sourceInfo.getAnyValue(BugzillaAttribute.PRODUCT, null);
      if (Util.NN(product).length() == 0) {
        throw new UploadException("no product specified", "Product Not Specified",
          "It's not possible to upload a bug without specifying its product");
      }
      @Nullable MultiMap<String, String> submitForm = getSubmitForm(product);
      bugInfo = new BugInfoForUpload(sourceInfo);
      long lastSubmitAttempt = myServerInfo.getPersistentLong(submitKey);
      BugInfo submitted = findAlreadySubmitted(bugInfo, lastSubmitAttempt);
      if (submitted == null) {
        registerSubmitAttempt(submitKey);
        newID = doSubmit(bugInfo, submitForm);
        if (newID == null) {
          assert false : bugInfo;
          throw new ConnectorException("newID null", "server did not return valid id",
            "server did not return valid id");
        }
        postCount++;
        adjustBugInfoForUpdate(bugInfo, newID);
      } else {
        newID = submitted.getID();
        // bug has been already submitted
        adjustBugInfoAfterBrokenSubmit(bugInfo, submitted, lastSubmitAttempt);
      }
      if (bugInfo.hasAnythingToUpdate()) {
        UpdateBug updater = new UpdateBug(myServerInfo, newID.toString(), bugInfo, myUsername);
        int n = updater.update(false);
        postCount += n;
      }
      return new BugSubmitResult(newID, null, postCount);
    } catch (ConnectorException e) {
      if (bugInfo != null) {
        long lastSubmitAttempt = myServerInfo.getPersistentLong(submitKey);
        BugInfo submitted = findAlreadySubmitted(bugInfo, lastSubmitAttempt);
        if (submitted != null) {
          newID = submitted.getID();
          adjustBugInfoAfterBrokenSubmit(bugInfo, submitted, lastSubmitAttempt);
        }
      }
      return new BugSubmitResult(newID, e, postCount);
    } finally {
      if (newID != null) {
        clearSubmitAttempt(submitKey);
      }
    }
  }

  private BugInfo findAlreadySubmitted(BugInfoForUpload bugInfo, long lastSubmitAttempt) throws ConnectorException {
    if (lastSubmitAttempt <= 0)
      return null;
    Log.debug("there was previous unsuccessful attempt to submit " + bugInfo + ", verifying");
    String url = myServerInfo.getBaseURL() + createQueryUrlToFindAlreadySubmittedBug(bugInfo, lastSubmitAttempt);
    LoadQuery loader = new LoadQuery(myMaterial, url, myAuthMaster);
    List<BugInfoMinimal> candidates;
    try {
      candidates = loader.loadBugs(new Progress());
    } catch (ConnectorException e) {
      Log.warn("failed to search for results of a previous submit attempt", e);
      throw e;
    }
    if (candidates.isEmpty())
      return null;
    List<Integer> ids = Collections15.arrayList();
    for (BugInfoMinimal candidate : candidates) {
      Integer id = candidate.getID();
      if (id == null) {
        continue;
      }
      ids.add(id);
    }
    if (ids.isEmpty())
      return null;
    Collections.sort(ids);
    int idCount = ids.size();
    Integer[] idArray;
    if (idCount > MAX_CANDIDATES) {
      idArray = new Integer[MAX_CANDIDATES];
      ids.subList(idCount - MAX_CANDIDATES, idCount).toArray(idArray);
    } else {
      idArray = ids.toArray(new Integer[idCount]);
    }
    LoadBugsXML xmlLoader =
      new LoadBugsXML(myServerInfo, idArray, new Progress(), "excludefield=attachment", false, true);
    Collection<BugInfo> candidateInfos;
    try {
      candidateInfos = xmlLoader.loadBugs();
    } catch (ConnectorException e) {
      Log.warn("failed to retrieve details about candidates for previous submit result", e);
      throw e;
    }
    BugInfo best = null;
    for (BugInfo info : candidateInfos) {
      Integer id = info.getID();
      if (id == null)
        continue;
      String creationTimestamp = info.getValues().getScalarValue(BugzillaAttribute.CREATION_TIMESTAMP, "");
      long time = BugzillaDateUtil.parseOrWarn(creationTimestamp, myServerInfo.getDefaultTimezone()).getTime();
      if (time - lastSubmitAttempt < -SUBMIT_TIME_DIFF_TOLERANCE) {
        // bad candidate, there will be no more good due to order by mtime desc
        Log.debug("sub " + bugInfo + ": can #" + id + " bad: time");
        continue;
      }
      // todo - maybe, check summary and other stuff? we assume they are correct from the query
      if (best == null) {
        best = info;
      } else {
        if (best.getID() < id)
          best = info;
      }
    }
    return best;
  }

  private String createQueryUrlToFindAlreadySubmittedBug(BugInfoForUpload bugInfo, long previousSubmitTime) {
    QueryURLBuilder builder = myIntegration.getURLQueryBuilder();
    builder.setOrderBy(ID_DESC);
    builder.addProductCondition(new String[] {bugInfo.getAnyValue(BugzillaAttribute.PRODUCT, "")});
    builder.addChangeDateCondition(previousSubmitTime - SUBMIT_TIME_DIFF_TOLERANCE);
    BooleanChart chart = new BooleanChart();
    BooleanChart.Group chgroup1 = new BooleanChart.Group();
    chgroup1.addElement(BooleanChart.createElement(BugzillaAttribute.REPORTER, EQUALS, myUsername));
    chart.addGroup(chgroup1);
    BooleanChart.Group chgroup2 = new BooleanChart.Group();
    chgroup2.addElement(BooleanChart.createElement(SHORT_DESCRIPTION, EQUALS, bugInfo.getAnyValue(SHORT_DESCRIPTION, "")));
    chart.addGroup(chgroup2);
    builder.addBooleanChart(chart);
    return builder.getURL();
  }

  private void clearSubmitAttempt(String submitKey) {
    myServerInfo.getStateStorage().removePersistent(submitKey);
  }

  private void registerSubmitAttempt(String submitKey) {
    myServerInfo.getStateStorage().setPersistentLong(submitKey, System.currentTimeMillis());
  }

  private String getSubmitKey(BugInfoForUpload bugInfo) {
    String product = Util.NN(bugInfo.getAnyValue(BugzillaAttribute.PRODUCT, null));
    String summary = Util.NN(bugInfo.getAnyValue(SHORT_DESCRIPTION, null));
    return "newbug:" + product + ":" + summary + ":" + myUsername;
  }

  private MultiMap<String, String> getSubmitForm(String product) {
    if (myServerInfo.getStateStorage().getRuntime(NO_SUBMIT_FORM) != null) {
      return null;
    }

    try {
      Element form = loadSubmitForm(myServerInfo.getBaseURL(), product);
      if (form == null) {
        throw new UploadException("cannot find post bug form", "", "");
      }
      ExtractFormParameters policy;
      BugzillaVersion bzVersion = myServerInfo != null ? myServerInfo.getBzVersion() : null;
      if (bzVersion != null && bzVersion.compareTo(BugzillaVersion.V4_0) >= 0) policy = ExtractFormParameters.FOR_SUBMIT_4_0;
      else policy = ExtractFormParameters.FOR_SUBMIT;
      return policy.perform(form);
    } catch (ConnectorException e) {
      Log.warn("cannot find submit bug form", e);
    }

    myServerInfo.getStateStorage().setRuntime(NO_SUBMIT_FORM, Boolean.TRUE);
    return null;
  }

  /**
   * This method removes all values that we don't know how to update.
   */
  private void adjustBugInfoForUpdate(BugInfoForUpload bugInfo, Integer newID) {
    bugInfo.getPrevValues().reput(BugzillaAttribute.ID, newID.toString());
    bugInfo.getPrevValues().reput(STATUS, BugzillaHTMLConstants.STATUS_AFTER_SUBMIT);

    BugzillaValues newValues = bugInfo.getNewValues();
    BugzillaValues prevValues = bugInfo.getPrevValues();
    Map<BugzillaAttribute, String> scalars = newValues.getScalars();
    BugzillaAttribute[] attributes = scalars.keySet().toArray(new BugzillaAttribute[scalars.size()]);
    for (BugzillaAttribute attribute : attributes) {
      if (hasNothingNewForAttribute(newValues, prevValues, attribute) || !isUpdatePossibleForAttribute(attribute)) {
        Log.debug("removing attribute " + attribute + " from update list");
        newValues.clear(attribute);
      }
    }

    bugInfo.clearGroups();
  }

  private void adjustBugInfoAfterBrokenSubmit(BugInfoForUpload bugInfo, BugInfo submitted, long lastSubmitAttempt) {
    Integer id = submitted.getID();
    if (id == null)
      return;
    bugInfo.getPrevValues().reput(BugzillaAttribute.ID, id.toString());
    String status = submitted.getValues().getMandatoryScalarValue(STATUS);
    bugInfo.getPrevValues().reput(STATUS, status);
    bugInfo.getNewValues().reput(STATUS, status);

    BugzillaValues newValues = bugInfo.getNewValues();
    Map<BugzillaAttribute, String> scalars = newValues.getScalars();
    BugzillaAttribute[] attributes = scalars.keySet().toArray(new BugzillaAttribute[scalars.size()]);
    for (BugzillaAttribute attribute : attributes) {
      if (!isUpdatePossibleForAttribute(attribute)) {
        Log.debug("removing attribute " + attribute + " from update list");
        newValues.clear(attribute);
      }
    }

    List<BugInfoForUpload.CommentForUpload> requestedComments = bugInfo.getComments();
    while (!requestedComments.isEmpty()) {
      String text = requestedComments.get(0).getComment();
      if (!OperUtils.isCommentSubmitted(text, submitted, lastSubmitAttempt, myUsername)) {
        break;
      }
      bugInfo.removeFirstComment();
    }
  }

  private boolean isUpdatePossibleForAttribute(BugzillaAttribute attribute) {
    return BugzillaHTMLConstants.UPDATE_ATTRIBUTE_REQUIRED_MAP.containsKey(attribute) ||
      BugzillaHTMLConstants.UPDATE_WORKFLOW_ATTRIBUTE_MAP.containsKey(attribute) ||
      BugzillaHTMLConstants.UPDATE_FORM_FIELD_NAMES_MAP.containsKey(attribute);
  }

  private boolean hasNothingNewForAttribute(BugzillaValues newValues, BugzillaValues prevValues,
    BugzillaAttribute attribute)
  {
    Object newValue = newValues.getValue(attribute);
    return (newValue == null)
      || ((newValue instanceof String) && ((String) newValue).trim().isEmpty())
      || Util.equals(newValue, prevValues.getValue(attribute));
  }

  private Integer doSubmit(final BugInfoForUpload bugInfo, final MultiMap<String, String> submitForm)
    throws ConnectorException
  {
    return runOperation(new RunnableRE<Integer, ConnectorException>() {
      public Integer run() throws ConnectorException {
        ParamAdjustments parameters = buildParametersForSubmit(bugInfo, submitForm);
        long submitTime = System.currentTimeMillis();
        DocumentLoader loader = getDocumentLoader(mySubmitURL, false);
        Document document = loader.httpPOST(parameters.adjust(submitForm)).loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "submitting a bug", false, false);
        document = processMatchRequest(document);
        Integer id = fetchBugID(document.getRootElement());

        if (id == null) {
          Log.warn("cannot get bug ID when submitting " + bugInfo + ", searching for new bug");
          BugInfo submitted = findAlreadySubmitted(bugInfo, submitTime);
          if (submitted != null) {
            id = submitted.getID();
          } else {
            BugzillaErrorDetector.throwAnyError(document, "submitting a bug");
            LogHelper.warning("No error detected on HTML page");
            throw new UploadException("server did not return bug id", L.content("Bad bug ID"), L.tooltip(
              "Bugzilla Server has not returned bug id. This may be a compatibility issue or a bug in the" +
                " application, please report to support team."));
          }
        }
        return id;
      }
    });
  }

  private Document processMatchRequest(Document document) throws ConnectorException {
    final List<NameValuePair> fixedParameters = OperUtils.getUnambiguousMatchRequestParameters(document, true);
    if (fixedParameters == null) {
      return document;
    }
    
    return runOperation(new RunnableRE<Document, ConnectorException>() {
      public Document run() throws ConnectorException {
        DocumentLoader loader = getDocumentLoader(mySubmitURL, false);
        loader.setScriptOverride("post_bug_fixmatch");
        Document document = loader.httpPOST(fixedParameters).loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "submitting a bug (after user match)", false, false);
        return document;
      }
    });
  }

  private Integer fetchBugID(Element root) {
    final Set<Integer> textIds = getIDsFromTexts(root);
    final Set<Integer> hrefIds = getIDsFromLinks(root);

    final Set<Integer> intersection = Collections15.hashSet(textIds);
    intersection.retainAll(hrefIds);
    if(intersection.size() == 1) {
      return intersection.iterator().next();
    }

    if(textIds.isEmpty() && hrefIds.isEmpty()) {
      // not a bug page probably
      return null;
    }

    if(textIds.isEmpty() && hrefIds.size() == 1) {
      // a non-English bugzilla probably
      return hrefIds.iterator().next();
    }

    // a false negative is not that good, but better than a false positive:
    // there will be a call to findAlreadySubmitted() later
    Log.warn("cannot fetch bug ID from bug page: textIds=" + textIds + "; hrefIds=" + hrefIds);
    return null;
  }

  private Set<Integer> getIDsFromTexts(Element root) {
    final Set<Integer> textIds = Collections15.hashSet();
    for(final Element e : JDOMUtils.searchElements(root, BUG_ID_TAGS)) {
      for(final Pattern p : BUG_ID_PATTERNS) {
        final Matcher m = p.matcher(JDOMUtils.getTextTrim(e));
        if(m.find()) {
          try {
            textIds.add(Integer.parseInt(m.group(1)));
          } catch (NumberFormatException ignored) {
            // fall through
          }
        }
      }
    }
    return textIds;
  }

  private Set<Integer> getIDsFromLinks(Element root) {
    final Set<Integer> hrefIds = Collections15.hashSet();
    for(final Element e : JDOMUtils.searchElements(root, A_TAG)) {
      final String href = JDOMUtils.getAttributeValue(e, "href", "", false);
      final Matcher m = HREF_PATTERN.matcher(href);
      if(m.find()) {
        try {
          hrefIds.add(Integer.parseInt(m.group(1)));
        } catch (NumberFormatException ignored) {
          // fall through
        }
      }
    }
    return hrefIds;
  }

  private ParamAdjustments buildParametersForSubmit(
    BugInfoForUpload bugInfo, MultiMap<String, String> submitForm) throws UploadException
  {
    ParamAdjustments parameters = new ParamAdjustments();

    BugzillaValues newValues = bugInfo.getNewValues();
    BugzillaValues prevValues = bugInfo.getPrevValues();

    buildScalars(newValues, prevValues, parameters, submitForm);
    buildCC(bugInfo, parameters);
    buildDescription(bugInfo, parameters);
    UpdateBug.addParametersForGroupChange(parameters, bugInfo);
    buildCustomFields(bugInfo, parameters, submitForm);

    // todo load from page
    for(final NameValuePair pair: BugzillaHTMLConstants.ADDITIONAL_SUBMIT_UPDATE_PAIRS) {
      parameters.replace(pair.getName(), pair.getValue());
    }

    return parameters;
  }

  private void buildCustomFields(BugInfoForUpload bugInfo, ParamAdjustments parameters,
    MultiMap<String, String> submitForm)
  {
    Map<String, List<String>> customFields = Collections15.linkedHashMap(bugInfo.getCustomFieldChanges());
    for (Map.Entry<String, List<String>> entry : customFields.entrySet()) {
      String name = entry.getKey();
      if (submitForm == null || !submitForm.containsKey(name)) {
        Log.warn("skipping parameter (custom field) " + name + " on submit");
        continue;
      }
      List<String> value = entry.getValue();
      if (value.isEmpty()) {
        parameters.clear(name);
      } else {
        parameters.replace(name, value);
      }
      bugInfo.clearCustomFieldChange(name);
    }
  }

  private void buildDescription(BugInfoForUpload bugInfo, ParamAdjustments parameters) {
    List<BugInfoForUpload.CommentForUpload> comments = bugInfo.getComments();
    if (comments.size() > 0) {
      BugInfoForUpload.CommentForUpload c = comments.get(0);
      parameters.replace(BugzillaHTMLConstants.SUBMIT_COMMENT_PARAMETER_NAME, OperUtils.reformatComment(c.getComment()));
      if (Boolean.TRUE.equals(c.getPrivacy())) {
        parameters.replace(BugzillaHTMLConstants.SUBMIT_COMMENT_PRIVACY_PARAMETER_NAME, "1");
        parameters.replace(BugzillaHTMLConstants.SUBMIT_COMMENT_PRIVACY_PARAMETER_NAME_BZ40, "1");
      }
      bugInfo.removeFirstComment();
    } else {
      // post empty description
      parameters.replace(BugzillaHTMLConstants.SUBMIT_COMMENT_PARAMETER_NAME, "");
      // throw new UploadException("cannot upload without comment", "Description Required",
      //  "A comment (Bug Description) is required at submit time. Please enter description and try again.");
    }
  }

  private void buildCC(BugInfoForUpload bugInfo, ParamAdjustments parameters) {
    final Collection<String> cc = OperUtils.removeEmailSuffix(bugInfo.getAddCC(), myServerInfo.getEmailSuffix());
    final String name = BugzillaHTMLConstants.MANDATORY_SUBMIT_FIELDS_MAP.get(BugzillaAttribute.CC);
    assert name != null;
    if(name != null && cc != null && !cc.isEmpty()) {
      // single string won't work with BZ 4.0; multiple strings work with 2.22, 3.0 and up, didn't check before 2.22
      // parameters.replace(name, StringUtil.implode(cc, BugzillaHTMLConstants.SUBMIT_CC_LIST_DELIMITER));
      parameters.replace(name, cc);
      bugInfo.clearAddedCC();
    }
  }


  private void buildScalars(BugzillaValues newValues, BugzillaValues prevValues, ParamAdjustments parameters,
    MultiMap<String, String> submitForm)
  {
    Map<BugzillaAttribute, String> map = newValues.getScalars();
    Set<BugzillaAttribute> set = map.keySet();
    BugzillaAttribute[] scalars = set.toArray(new BugzillaAttribute[set.size()]);
    for (BugzillaAttribute attribute : scalars) {
      String value = map.get(attribute);
      value = myServerInfo.removeSuffix(attribute, value);
      String name = BugzillaHTMLConstants.MANDATORY_SUBMIT_FIELDS_MAP.get(attribute);
      if (name == null) {
        if (Util.NN(value).length() == 0) {
          // empty
          continue;
        }
        name = BugzillaHTMLConstants.OPTIONAL_SUBMIT_FIELDS_MAP.get(attribute);
        if (name == null) {
          // don't know how to submit
          continue;
        }
        if (submitForm == null || !submitForm.containsKey(name)) {
          Log.debug("skipping parameter " + name + " on submit");
          continue;
        }
      }
      parameters.replace(name, Util.NN(value));
      // move attribute to old values
      newValues.clear(attribute);
      prevValues.reput(attribute, value);
    }
  }
}