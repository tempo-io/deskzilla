package com.almworks.bugzilla.integration.oper;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.connector.http.DocumentLoader;
import com.almworks.api.connector.http.HtmlUtils;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.err.*;
import com.almworks.items.sync.ItemUploader;
import com.almworks.util.*;
import com.almworks.util.collections.*;
import com.almworks.util.progress.Progress;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.apache.commons.httpclient.NameValuePair;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.almworks.bugzilla.integration.BugzillaAttribute.STATUS;
import static com.almworks.bugzilla.integration.BugzillaHTMLConstants.UPDATE_WORKFLOW_ATTRIBUTE_MAP;

public class UpdateBug extends BugzillaOperation {

  private final String myUrl;
  private final BugInfoForUpload myUpdateInfo;
  private final LoadFrontPage myFrontPageLoader;

  private ParamAdjustments myRequiredParameters = null;

  private static final String DEFAULT_TIMESTAMP = "19700101000000";
  private static final Convertor<NameValuePair, String> NVP_TO_STRING = new Convertor<NameValuePair, String>() {
    public String convert(NameValuePair nameValuePair) {
      return nameValuePair.getName();
    }
  };
  private final String myId;
  private static final Pattern ATTACHMENT_UPLOADED_ID_PATTERN =
    Pattern.compile("attachment\\.cgi\\?id\\=(\\d+)\\&action\\=edit.*");
  private static final String[] COLLISION_TITLES = new String[] {"MID-AIR", "COLLISION"};

  private static final Set<String> PRE_BZ31_KNOBS = Collections15.linkedHashSet(new String[] {
    BugzillaHTMLConstants.KNOB_ACCEPT, BugzillaHTMLConstants.KNOB_CLOSE, BugzillaHTMLConstants.KNOB_CONFIRM,
    BugzillaHTMLConstants.KNOB_REASSIGN, BugzillaHTMLConstants.KNOB_REOPEN, BugzillaHTMLConstants.KNOB_RESOLVE,
    BugzillaHTMLConstants.KNOB_VERIFY});

  private static final Set<String> STANDARD_BZ31_KNOBS = Collections15.linkedHashSet(new String[] {
    BugStatus.ASSIGNED.getName(), BugStatus.CLOSED.getName(), BugStatus.NEW.getName(), BugStatus.REOPENED.getName(),
    BugStatus.RESOLVED.getName(), BugStatus.UNCONFIRMED.getName(), BugStatus.VERIFIED.getName()});
  private final ServerInfo myServerInfo;
  private final String myUsername;
  private BugInfo myBugInfo;

  public UpdateBug(ServerInfo info, String id, BugInfoForUpload updateInfo, String username)
  {
    super(info);
    myServerInfo = info;
    myUsername = username;
    assert id != null;
    assert updateInfo != null;
    myId = id;
    myUrl = info.getBaseURL() + BugzillaHTMLConstants.URL_UPDATE_BUG;
    myUpdateInfo = updateInfo;
    myFrontPageLoader = new LoadFrontPage(info, info.getBaseURL() + BugzillaHTMLConstants.URL_FRONT_PAGE + id, id);
  }

  public int update(boolean haveValidDeltaTs) throws ConnectorException {
    int pass = 0;
    String deltats = getDeltaTs(haveValidDeltaTs);

    FrontPageData frontPageData = myFrontPageLoader.loadFrontPage();
    String updateKey = getUpdateKey();
    long lastUpdateAttemptTime = getOrUpdateLastUpdateAttempt(updateKey);
    List<ParamAdjustments> parametersList = buildParameters(frontPageData, lastUpdateAttemptTime);

    for (final ParamAdjustments parameters : parametersList) {
      pass++;
      if (pass > 1) {
        frontPageData = myFrontPageLoader.loadFrontPage();
      }

      String realDeltats = frontPageData.getDeltaTs();
      assert pass >= 1;
      if (pass == 1 && haveValidDeltaTs) {
        // first POST in a sequence
        if (!deltats.equals(realDeltats)) {
          Log.debug("delta ts differ, local: " + deltats + ", server: " + realDeltats);
          throw new MidAirCollisionException(realDeltats);
        }
      } else {
        // second and consequent POSTs
        // todo assert here that no intervening changes are likely. revise!
        deltats = realDeltats;
      }

      // todo make sure parameters do not contain delta_ts
      parameters.replace(BugzillaHTMLConstants.UPDATE_DELTA_TS_PARAMETER, deltats);
      parameters.replace("confirm_product_change", "1");
      final MultiMap<String, String> paramMap = parameters.adjust(frontPageData.getFormParameters());
      OperUtils.propagateResolutions31(paramMap);
      try {
        Document document = runOperation(new RunnableRE<Document, ConnectorException>() {
          public Document run() throws ConnectorException {
            Document document = getDocumentLoader(myUrl, false).httpPOST(paramMap).loadHTML();
            BugzillaErrorDetector.detectAndThrow(document, "updating a bug");
            return document;
          }
        });

        document = processMatchRequest(document);
        document = checkProductChangeVerification(document, paramMap, true);
        checkCollision(document);
        checkDataUploaded50(document);
      } catch (ConnectorException e) {
        // todo exception in the middle of update cycle - ?
        throw e;
      }
    }
    // handle attachments
    // todo verify attachment is not already uploaded
    if (myUpdateInfo.hasAttachments())
      pass += uploadAttachments();

    uploadVoting();

    clearUpdateAttemptInfo(updateKey);

    return pass;
  }

  /**
   * Checks that Bugzilla has accepted the values. Assuming that this step is not required for BZ version < 5.0
   */
  private void checkDataUploaded50(Document document) throws BugzillaErrorException {
    BugzillaVersion version = myServerInfo != null ? myServerInfo.getBzVersion() : null;
    if (version == null || version.compareTo(BugzillaVersion.V5_0) < 0) return;
    Iterator<Element> aIt = JDOMUtils.searchElementIterator(document.getRootElement(), "a");
    while (aIt.hasNext()) {
      Element a = aIt.next();
      String href = JDOMUtils.getAttributeValue(a, "href", "", true);
      if (href.isEmpty()) continue;
      if (!href.endsWith("show_bug.cgi?id=" + myId)) continue;
      return;
    }
    LogHelper.warning("Bug ID not found after update - assuming error state", myId);
    BugzillaErrorDetector.throwAnyError(document, "updating a bug");
    LogHelper.warning("No error has been detected", myId);
  }

  private void uploadVoting() throws ConnectorException {
    final Integer vote = myUpdateInfo.getVoteValue();
    if (vote == null) {
      return;
    }

    Boolean r = runOperation(new RunnableRE<Boolean, ConnectorException>() {
      public Boolean run() throws ConnectorException {
        DocumentLoader loader =
          getDocumentLoader(myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_USER_VOTES + myId, true, "vote.pre");
        Document html = loader.httpGET().loadHTML();
        Element form = findVotingForm(html.getRootElement());
        if (form == null)
          return false;
        MultiMap<String, String> parameters = HtmlUtils.extractDefaultFormParameters(form);
        parameters.removeAll(myId);
        parameters.add(myId, String.valueOf(vote));
        Log.debug(UpdateBug.this + ": sending votes [" + parameters + "]");
        loader = getDocumentLoader(myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_VOTE_POST, true, "vote");
        Document document = loader.httpPOST(parameters).loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "updating votes");
        return true;
      }
    });

    if (r == null || !r) {
      Log.warn(this + ": cannot vote");
    }
  }

  private Element findVotingForm(Element root) {
    Iterator<Element> ii = JDOMUtils.searchElementIterator(root, "form");
    while (ii.hasNext()) {
      Element form = ii.next();
      String action = JDOMUtils.getAttributeValue(form, "action", null, true);
      if (BugzillaHTMLConstants.isVoteAction(action))
        return form;
    }
    return null;
  }

  private void clearUpdateAttemptInfo(String updateKey) {
    myServerInfo.getStateStorage().removePersistent(updateKey);
  }

  private long getOrUpdateLastUpdateAttempt(String updateKey) {
    long lastAttempt = myServerInfo.getPersistentLong(updateKey);
    if (lastAttempt <= 0) {
      myServerInfo.getStateStorage().setPersistentLong(updateKey, System.currentTimeMillis());
      return 0;
    } else {
      return lastAttempt;
    }
  }

  private String getUpdateKey() {
    return "update:" + myId;
  }

  private String getDeltaTs(boolean haveValidDeltaTs) throws UploadException {
    String deltats = Util.NN(myUpdateInfo.getNewValues().getScalarValue(BugzillaAttribute.DELTA_TS, null));
    if (deltats.length() == 0) {
      deltats = Util.NN(myUpdateInfo.getPrevValues().getScalarValue(BugzillaAttribute.DELTA_TS, null));
    }
    if (haveValidDeltaTs && deltats.length() == 0)
      throw new UploadException("timestamp required", L.content("Timestamp required"), L.tooltip(
        "An error occurred that prevents bug upload.\n " + "Please check that Bugzilla server is available \n" +
          "and allows to update this bug." + "\n\n" + "(Timestamp is required for bug upload)"));
    return deltats;
  }

  private Document processMatchRequest(Document document) throws ConnectorException {
    final List<NameValuePair> fixedParameters = OperUtils.getUnambiguousMatchRequestParameters(document, false);
    if (fixedParameters == null)
      return document;
    Document newDocument = runOperation(new RunnableRE<Document, ConnectorException>() {
      public Document run() throws ConnectorException {
        DocumentLoader loader = getDocumentLoader(myUrl, false);
        loader.setScriptOverride("process_bug_fixmatch");
        Document document = loader.httpPOST(fixedParameters).loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "updating a bug (after user match)");
        return document;
      }
    });
    return newDocument;
  }

  private int uploadAttachments() throws ConnectorException {
    BugInfoForUpload.AttachData[] attachments = myUpdateInfo.getAttachments();
    int pass = 0;
    for (int i = 0; i < attachments.length; i++) {
      BugInfoForUpload.AttachData attachment = attachments[i];
      uploadAttachment(attachment);
      pass++;
    }
    return pass;
  }

  private void uploadAttachment(final BugInfoForUpload.AttachData attachment) throws ConnectorException {
    LoadCreateAttachmentPage loader = new LoadCreateAttachmentPage(myMaterial, myServerInfo.getBaseURL(), myId, myAuthMaster);
    MultiMap<String, String> defaultParameters = loader.loadDefaultFormParameters();

    final ParamAdjustments parameters = new ParamAdjustments();
    parameters.replace("bugid", myId);
    parameters.replace("action", "insert");
    parameters.replace("description", Util.NN(attachment.getDescription()));
    parameters.replace("contenttypemethod", "manual");
    parameters.replace("contenttypeentry", Util.NN(attachment.getMimeType()));

    final MultiMap<String, String> paramMap = parameters.adjust(defaultParameters);

    final String url = myServerInfo.getBaseURL() + BugzillaHTMLConstants.URL_UPLOAD_ATTACHMENT;
    Integer attachmentID = runOperation(new RunnableRE<Integer, ConnectorException>() {
      public Integer run() throws ConnectorException {
        DocumentLoader loader = getDocumentLoader(url, false);
        loader.httpMultipartPOST(paramMap, "data", attachment.getFile(), attachment.getMimeType());
        Document document = loader.loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "attachment upload");

        Integer attachmentID = null;
        Iterator<Element> ii = JDOMUtils.searchElementIterator(document.getRootElement(), "A");
        while (ii.hasNext()) {
          Element element = ii.next();
          String href = JDOMUtils.getAttributeValue(element, "href", null, true);
          if (href == null)
            continue;
          Matcher matcher = ATTACHMENT_UPLOADED_ID_PATTERN.matcher(href);
          if (matcher.matches()) {
            String id = matcher.group(1);
            try {
              attachmentID = Integer.valueOf(id);
              break;
            } catch (NumberFormatException e) {
              Log.debug("strange href: " + href);
            }
          }
        }
        if (attachmentID == null) {
          throw new ConnectorException("bugzilla did not return attachment id", "Attachment upload failed",
            "Attachment upload failed.\n\nWhile the upload request has been successful, and the attachment\n" +
              "has been uploaded to server, Bugzilla did not return attachment id.\n" +
              "This problem may be caused by heavy customization of Bugzilla.");
        }
        return attachmentID;
      }
    });
    ItemUploader.UploadProcess uploadProcess = myUpdateInfo.getUploadProcess();
    if (uploadProcess != null) {
      SuccessfulUploadHook hook = attachment.getHook();
      if (hook != null) {
        String attachmentURL = BugzillaIntegration.getDownloadAttachmentURL(myServerInfo.getBaseURL(), attachmentID);
        hook.onSuccessfulUpload(attachmentID, attachmentURL, attachment, uploadProcess);
      }
    }
  }

  private Document checkProductChangeVerification(Document document, MultiMap<String, String> parameters,
    boolean allowConfirm) throws ConnectorException
  {
    final Element root = document.getRootElement();
    Element form = OperUtils.findUpdateFormElement(root, true);
    if (form == null)
      return document;
    List<Element> selects = JDOMUtils.searchElements(form, "select");
    int selectCount = selects.size();
    if (selectCount < 2) {
      return document;
    }
    for (Iterator<Element> ii = selects.iterator(); ii.hasNext();) {
      Element select = ii.next();
      String name = JDOMUtils.getAttributeValue(select, "name", null, false);
      BugzillaAttribute attribute = BugzillaHTMLConstants.HTML_SELECTION_NAME_ATTRIBUTE_MAP.get(name);
      if (attribute == null) {
        return document;
      }
      if (!BugzillaAttribute.PRODUCT_DEPENDENT_ATTRIBUTES.contains(attribute)) {
        // unknown select?
        return document;
      }
    }
    if (!allowConfirm)
      throw new InconsistentProductChange();

    final MultiMap<String, String> map = HtmlUtils.extractDefaultFormParameters(form);
    for (BugzillaAttribute attribute : BugzillaAttribute.PRODUCT_DEPENDENT_ATTRIBUTES) {
      String fieldName = BugzillaHTMLConstants.UPDATE_FORM_FIELD_NAMES_MAP.get(attribute);
      if (fieldName == null) {
        assert false : attribute;
        continue;
      }
      for (Pair<String, String> pair : parameters) {
        if (fieldName.equals(pair.getFirst())) {
          String formValue = map.getSingle(fieldName);
          if (formValue == null && attribute.isOptional()) {
            // bypass verification
            break;
          }
          if (!Util.equals(formValue, pair.getSecond())) {
            throw new InconsistentProductChange();
          }
        }
      }
    }

    // proceed with confirmation
    document = runOperation(new RunnableRE<Document, ConnectorException>() {
      public Document run() throws ConnectorException {
        DocumentLoader loader = getDocumentLoader(myUrl, true, "move_confirm");
        Document document = loader.httpPOST(map).loadHTML();
        BugzillaErrorDetector.detectAndThrow(document, "updating a bug");
        return document;
      }
    });
    document = processMatchRequest(document);
    document = checkProductChangeVerification(document, parameters, false);
    return document;
  }

  private void checkCollision(Document document) throws MidAirCollisionException {
    String id = myUpdateInfo.getAnyValue(BugzillaAttribute.ID, "");
    if (id.length() == 0)
      return;
    if (hasCollision(document, id)) {
      Element delta_ts = JDOMUtils.searchElement(document.getRootElement(), "input", "name", "delta_ts");
      if (delta_ts != null) {
        String timestamp = JDOMUtils.getAttributeValue(delta_ts, "value", DEFAULT_TIMESTAMP, true);
        assert timestamp != DEFAULT_TIMESTAMP : myUrl;
        throw new MidAirCollisionException(timestamp);
      }

      assert false : this;
      throw new MidAirCollisionException(DEFAULT_TIMESTAMP);
    }
  }

  static boolean hasCollision(Document document, String id) {
    // if titles are in english, this will work
    int weight = OperUtils.searchTitles(document.getRootElement(), COLLISION_TITLES);
    if (weight > 0)
      return true;

    // look for a form under <li> with all inputs hidden, and a reference to bug's id in another li
    Iterator<Element> formIterator = JDOMUtils.searchElementIterator(document.getRootElement(), "form");
    while (formIterator.hasNext()) {
      Element form = formIterator.next();
      if (isOverwriteCollisionForm(form, id)) {
        Log.debug("collision: " + form);
        return true;
      }
    }
    return false;
  }

  private static boolean isOverwriteCollisionForm(Element form, String id) {
    // is form is under <li> ?
    Element li = JDOMUtils.getAncestor(form, "li", 5);
    if (li == null)
      return false;
    Log.debug("UB:checkCollision:found <li><form>");

    // does it have only hidden inputs? (and buttons)
    Iterator<Element> inputIterator = JDOMUtils.searchElementIterator(form, "input");
    boolean foundUserInput = false;
    int hiddenCount = 0;
    while (inputIterator.hasNext()) {
      Element input = inputIterator.next();
      String inputType = JDOMUtils.getAttributeValue(input, "type", "text", false);
      if ("hidden".equalsIgnoreCase(inputType)) {
        hiddenCount++;
      } else if (!"submit".equalsIgnoreCase(inputType) && !"reset".equalsIgnoreCase(inputType) &&
        !"button".equalsIgnoreCase(inputType))
      {
        foundUserInput = true;
        break;
      }
    }
    if (foundUserInput || hiddenCount < 2)
      return false;
    Log.debug("UB:checkCollision: form inputs good");

    // is there another <li> with the link to the bug?
    Element list = li.getParentElement();
    if (list == null)
      return false;
    List<Element> allLis = JDOMUtils.getChildren(list, "li");
    String lookFor = BugzillaHTMLConstants.URL_FRONT_PAGE + id;
    boolean foundLink = false;
    for (Element anotherLi : allLis) {
      if (anotherLi == li)
        continue;
      Iterator<Element> aIterator = JDOMUtils.searchElementIterator(anotherLi, "a");
      while (aIterator.hasNext()) {
        Element a = aIterator.next();
        String href = JDOMUtils.getAttributeValue(a, "href", null, false);
        if (href != null && href.endsWith(lookFor)) {
          foundLink = true;
          break;
        }
      }
      if (foundLink)
        break;
    }
    if (!foundLink)
      return false;
    Log.debug("UB:checkCollision: found rollback link");
    return true;
  }


  private List<ParamAdjustments> buildParameters(FrontPageData frontPageData, long lastUpdateAttemptTime)
    throws ConnectorException
  {
    List<ParamAdjustments> result = Collections15.arrayList();
    result.add(buildFirstRequestParams(frontPageData, lastUpdateAttemptTime));
    addWorkflow(result, frontPageData);
    addComments(result, lastUpdateAttemptTime, frontPageData);
    addCreateFlags(result, frontPageData);
    return result;
  }

  private void addCreateFlags(List<ParamAdjustments> result, FrontPageData fPD) throws UploadException {
    while (myUpdateInfo.hasFlagsToAdd()) {
      ParamAdjustments params = new ParamAdjustments();
      result.add(params);
      addParametersToAddFlags(params, fPD);
    }
  }

  private void addComments(List<ParamAdjustments> result, long lastUpdateAttemptTime,
    FrontPageData frontPageData) throws UploadException
  {
    List<BugInfoForUpload.CommentForUpload> comments = myUpdateInfo.getComments();
    int commentCount = comments.size();
    if (commentCount > 1) {
      for (int i = 1; i < commentCount; i++) {
        BugInfoForUpload.CommentForUpload c = comments.get(i);
        String comment = c.getComment();
        Boolean privacy = c.getPrivacy();
        BigDecimal timeWorked = c.getTimeWorked();
        if (isCommentAlreadySubmitted(comment, lastUpdateAttemptTime)) {
          continue;
        }
        comment = OperUtils.reformatComment(comment);
        ParamAdjustments parameters = buildRequiredParameters(frontPageData, true);
        addComment(parameters, comment, privacy, timeWorked);
        result.add(parameters);
      }
    }
  }

  private ParamAdjustments buildFirstRequestParams(FrontPageData frontPageData, long lastUpdateAttemptTime) throws UploadException{
    ParamAdjustments parameters = buildRequiredParameters(frontPageData, true);
    addParametersForScalars(parameters);
    addParametersForFirstComment(parameters, lastUpdateAttemptTime);
    addParametersForCC(parameters);
    addParametersForGroupChange(parameters, myUpdateInfo);
    addParametersForCommentsPrivacyChange(parameters, frontPageData);
    addParametersForCustomFields(parameters);
    addParametersForSeeAlso(parameters, frontPageData);
    addParametersForFirstFlags(parameters, frontPageData);
    return parameters;
  }

  private void addParametersForFirstFlags(ParamAdjustments parameters, FrontPageData fPD) throws UploadException {
    Iterator<Map.Entry<Integer,BugInfoForUpload.FlagState>> it = myUpdateInfo.getChangedFlags();
    while (it.hasNext()) {
      Map.Entry<Integer,BugInfoForUpload.FlagState> entry = it.next();
      Integer id = entry.getKey();
      FrontPageData.FlagInfo flag = BugInfo.Flag.findById(fPD.getFlags(), id);
      String name = entry.getValue().myFlagName;
      if (flag == null) throw new UploadException("Flags change upload failed", "Missing flag " + name + "#" + id + " on bug page", "Missing flag " + name + "#" + id + " on bug page");
      char status = entry.getValue().myStatus;
      String requestee = entry.getValue().myRequestee;
      checkFlagUploadPossible(flag, status, requestee);
      if (flag.getStatus() == status && Util.equals(flag.getRequestee(), requestee)) continue;
      parameters.replace("flag-" + id, String.valueOf(status));
      requestee = myServerInfo.removeSuffix(requestee);
      parameters.replace("requestee-" + id, requestee);
    }
    addParametersToAddFlags(parameters, fPD);
  }

  private void checkFlagUploadPossible(FrontPageData.FlagInfo flag, char status, String requestee) throws UploadException {
    if (ArrayUtil.indexOf(flag.getAllStatuses(), status) < 0) {
      throw new UploadException("Upload not possible",
        "Invalid status " + status + " for flag " + flag.getName(),
        "Status " +status + " for flag " + getFlagFullInfo(flag) +" is invalid");
    }
  }

  private String getFlagFullInfo(FrontPageData.FlagInfo flagOrType) {
    return flagOrType.getName() + "#" + (flagOrType.isType() ? flagOrType.getTypeId() : flagOrType.getFlagId()) +
      " (" + flagOrType.getDescription() + ")";
  }

  private void addParametersToAddFlags(ParamAdjustments parameters, FrontPageData fPD) throws UploadException {
    Iterator<Pair<Integer, BugInfoForUpload.FlagState>> flags = myUpdateInfo.getAddedFlags();
    IntArray addedTypes = new IntArray();
    List<Pair<Integer, BugInfoForUpload.FlagState>> addedFlags = Collections15.arrayList();
    while (flags.hasNext()) {
      Pair<Integer, BugInfoForUpload.FlagState> flag = flags.next();
      Integer id = flag.getFirst();
      BugInfoForUpload.FlagState flagState = flag.getSecond();
      char status = flagState.myStatus;
      if (status == 'X') {
        Log.error("Attempt to create cleared flag ignored " + flag);
        addedFlags.add(flag);
        continue;
      }
      String requestee = flagState.myRequestee;
      if (addedTypes.contains(id)) continue;
      addedTypes.add(id);
      FrontPageData.FlagInfo type = BugInfo.Flag.findTypeById(fPD.getFlags(), id);
      if (type == null) {
        String flagName = flagState.myFlagName;
        FrontPageData.FlagInfo existing = BugInfo.Flag.findFirstFlagByName(fPD.getFlags(), flagName);
        String fullInfo = existing != null ? getFlagFullInfo(existing) : flagName + "#" + id;
        if (existing != null) throw new UploadException("Upload not possible.",
          "Flag " + flagName + " is not multiplicable",
          "A flag " + fullInfo + " is already set. The flag type is not multiplicable. No other flags of the flag type can be set");
        else throw new UploadException("Upload not possible.",
          "Cannot set flag " + flagName + ".",
          "A flag " + fullInfo + " cannot be set because it is not applicable or inactive.");
      }
      addedFlags.add(flag);
      parameters.replace("flag_type-" + id, String.valueOf(status));
      requestee = myServerInfo.removeSuffix(requestee);
      parameters.replace("requestee_type-" + id, requestee);
    }
    myUpdateInfo.removeCreateFlags(addedFlags);
  }

  private void addParametersForSeeAlso(ParamAdjustments parameters, FrontPageData frontPageData)
    throws UploadException
  {
    BugzillaValues bv = myUpdateInfo.getNewValues();
    if (!bv.contains(BugzillaAttribute.SEE_ALSO))
      return;

    if (!frontPageData.isSeeAlsoEnabled()) {
      // we may have switched See Also off after editing it; in this case
      // there will be an upload error that we can't fix other than discarding all changes
      // (because there is no more See Also on Edit form)
      Log.warn("Update: skipping changes to See Also because the field is switched off");
      return;
    }

    List<String> newValues = bv.getTupleValues(BugzillaAttribute.SEE_ALSO);
    List<String> currentValues = frontPageData.getCurrentSeeAlso();
    if (currentValues == null)
      currentValues = Collections.emptyList();

    // remove
    final List<String> remove = Collections15.arrayList();
    for (String url : currentValues) {
      if (!newValues.contains(url)) {
        remove.add(url);
      }
    }
    if(!remove.isEmpty()) {
      parameters.replace("remove_see_also", remove);
    }

    // add
    StringBuilder b = new StringBuilder();
    for (String url : newValues) {
      if (!currentValues.contains(url)) {
        if (b.length() > 0)
          b.append(", ");
        b.append(url);
      }
    }
    parameters.replace("see_also", b.toString());
  }

  private void addParametersForCustomFields(ParamAdjustments parameters) {
    Map<String, List<String>> changes = myUpdateInfo.getCustomFieldChanges();
    for (Map.Entry<String, List<String>> e : changes.entrySet()) {
      List<String> value = e.getValue();
      if (value.isEmpty()) {
        parameters.clear(e.getKey());
      } else {
        parameters.replace(e.getKey(), value);
      }
    }
  }

  private void addParametersForCommentsPrivacyChange(ParamAdjustments parameters, FrontPageData frontPageData) {
    List<BugInfoForUpload.CommentChangeData> privacyChanges = myUpdateInfo.getCommentPrivacyChanges();
    if (privacyChanges == null || privacyChanges.size() == 0)
      return;
    final List<FrontPageData.CommentInfo> frontPageComments = frontPageData.getCommentPrivacyInfo();
    if (frontPageComments == null) {
      Log.warn("cannot change privacy for comments (no form fields)");
      return;
    }
    for (final BugInfoForUpload.CommentChangeData privacyChange : privacyChanges) {
      CommentsMatcher matcher = new CommentsMatcher(privacyChange.date) {
        protected boolean compareToSample(int index) {
          FrontPageData.CommentInfo commentInfo = frontPageComments.get(index);
          return privacyChange.textHash == commentInfo.getTextHash();
        }
      };
      for (int i = 0; i < frontPageComments.size(); i++) {
        FrontPageData.CommentInfo commentInfo = frontPageComments.get(i);
        matcher.acceptComment(i, commentInfo.date);
      }
      int found = matcher.search();
      if (found < 0) {
        Log.warn("cannot upload comment privacy change " + privacyChange);
        continue;
      }
      assert found >= 0 && found < frontPageComments.size() : found + " " + frontPageComments;

      FrontPageData.CommentInfo commentInfo = frontPageComments.get(found);
      if(privacyChange.privacy) {
        parameters.replace("isprivate-" + commentInfo.commentId, "1");
        parameters.replace("isprivate_" + commentInfo.commentId, "1");
      } else {
        parameters.clear("isprivate-" + commentInfo.commentId);
        parameters.clear("isprivate_" + commentInfo.commentId);
      }
    }

    myUpdateInfo.clearCommentPrivacyChanges();
  }

  static void addParametersForGroupChange(ParamAdjustments parameters, BugInfoForUpload updateInfo) {
    final Map<BugGroupData, Boolean> changes = updateInfo.getGroupChanges();
    for(final Map.Entry<BugGroupData, Boolean> entry : changes.entrySet()) {
      final BugGroupData group = entry.getKey();

      final String formId = group.getFormId();
      assert BugzillaHTMLConstants.isValidGroupFormId(formId) : formId;

      final Boolean v = entry.getValue();
      assert v != null;

      final String name = group.getName();
      if(name != null) {
        if(Boolean.TRUE.equals(v)) {
          parameters.add("groups", name);
        } else {
          parameters.remove("groups", name);
        }
      } else {
        if(Boolean.TRUE.equals(v)) {
          parameters.replace(formId, "1");
        } else {
          parameters.clear(formId);
        }
      }
    }
  }

  private void addWorkflow(List<ParamAdjustments> parametersList, FrontPageData frontPageData)
    throws UploadException
  {
    assert parametersList.size() == 1;
    boolean bz32 = frontPageData.getAllowedStatusChanges() != null;
    if (bz32) {
      addWorkflowBugzilla32(parametersList, frontPageData);
    } else {
      addWorkflowPreBugzilla32(parametersList, frontPageData);
    }
  }

  private void addWorkflowBugzilla32(List<ParamAdjustments> parametersList, FrontPageData frontPageData) {
    try {
      assert parametersList.size() > 0;
      ParamAdjustments parameters = parametersList.get(0);
      for (BugzillaAttribute bzattr : UPDATE_WORKFLOW_ATTRIBUTE_MAP.keySet()) {
        Pair<String, String> pair = getWorkflowAttribute(bzattr, false);
        if (pair.getSecond() != null) {
          // dup_id since bugzilla 3.2
          String name = bzattr == BugzillaAttribute.DUPLICATE_OF ? "dup_id" : UPDATE_WORKFLOW_ATTRIBUTE_MAP.get(bzattr);
          parameters.replace(name, myServerInfo.removeSuffix(bzattr, pair.getSecond()));
        }
      }
    } catch (BadWorkflowChange e) {
      // ignore
    }
  }

  private void addWorkflowPreBugzilla32(List<ParamAdjustments> parametersList, FrontPageData frontPageData)
    throws UploadException
  {
    Pair<String, String> changeStatus = getWorkflowAttribute(STATUS, true);
    Pair<String, String> changeResolution = getWorkflowAttribute(BugzillaAttribute.RESOLUTION, false);
    Pair<String, String> changeAssignedTo = getWorkflowAttribute(BugzillaAttribute.ASSIGNED_TO, false);
    Pair<String, String> changeDuplicateOf = getWorkflowAttribute(BugzillaAttribute.DUPLICATE_OF, false);

    List<String> knobs = frontPageData.getKnobs();
    // bugzilla 3.1+ uses other values for knobs
    boolean bz31 = detectBugzilla31(knobs);

    try {
      Status oldStatus = status(changeStatus.getFirst(), bz31);
      Status newStatus = status(changeStatus.getSecond(), bz31);

      if (newStatus == null) {
        // done
        flowKeepStatus(parametersList, oldStatus, changeResolution, changeAssignedTo, changeDuplicateOf, knobs, bz31,
          frontPageData);
      } else {
        if (newStatus.isOpen()) {
          if (oldStatus.isOpen()) {
            flowFromOpenToOpen(parametersList, oldStatus, newStatus, changeResolution, changeAssignedTo, knobs, bz31,
              frontPageData);
          } else {
            flowClosedToOpen(parametersList, newStatus, changeResolution, changeAssignedTo, knobs, bz31, frontPageData);
          }
        } else {
          flowToClosed(parametersList, newStatus, changeResolution, changeAssignedTo, changeDuplicateOf, knobs, bz31,
            frontPageData);
        }
      }
    } catch (BugStatus.BadName e) {
      throw new UnsupportedStatusException(e.getRequestedName());
    }
  }

  private Status status(String name, boolean bz31) throws BugStatus.BadName {
    if (name == null)
      return null;
    BugStatus status = null;
    try {
      status = BugStatus.forName(name);
    } catch (BugStatus.BadName e) {
      if (!bz31)
        throw e;
    }
    return new Status(status, name);
  }

  private static class Status {
    @Nullable
    private final BugStatus status;
    private final String name;

    private Status(BugStatus status, String statusString) {
      this.status = status;
      name = statusString;
    }

    public boolean isClosed() {
      // default: assume all custom statuses are open
      return status != null && status.isClosed();
    }

    public String getName() {
      return name;
    }

    public BugStatus getStatus() {
      return status;
    }

    public boolean isOpen() {
      return !isClosed();
    }

    public String toString() {
      return name != null ? name : String.valueOf(status);
    }
  }

  private boolean detectBugzilla31(List<String> knobs) {
    for (String knob : knobs) {
      if (PRE_BZ31_KNOBS.contains(knob))
        return false;
    }
    for (String knob : knobs) {
      if (STANDARD_BZ31_KNOBS.contains(knob))
        return true;
      if (knob.equals(Util.upper(knob)))
        return true;
    }
    return false;
  }

  private void flowKeepStatus(List<ParamAdjustments> parametersList, Status oldStatus,
    Pair<String, String> changeResolution, Pair<String, String> changeAssignedTo,
    Pair<String, String> changeDuplicateOf, List<String> knobs, boolean bz31, FrontPageData frontPageData)
    throws UploadException
  {
    String newResolution = changeResolution.getSecond();
    if (newResolution != null) {
      if (newResolution.equals("")) {
        //todo n/a?
        ParamAdjustments parameters = getNextWorkflowRequest(parametersList, frontPageData);
        // clearresolution remains in 3.1
        setAction(parameters, BugzillaAction.CLEAR_RESOLUTION, null, knobs, bz31);
      } else {
        if (!oldStatus.isClosed())
          throw new BadWorkflowChange("Cannot change resolution having status " + oldStatus);
        doResolveBug(parametersList, newResolution, knobs, bz31, oldStatus, true, false, frontPageData);
        finishResolved(parametersList, oldStatus, bz31, frontPageData);
      }
    } else if (changeDuplicateOf.getSecond() != null) {
      if (!oldStatus.isClosed())
        throw new BadWorkflowChange("Cannot change duplicate_of when status is open");
      doResolveDuplicateBug(parametersList, changeResolution, knobs, bz31, oldStatus, true, frontPageData);
      finishResolved(parametersList, oldStatus, bz31, frontPageData);
    }

    if (changeAssignedTo.getSecond() != null) {
      // status may change
      doReassignBug(parametersList, changeAssignedTo.getSecond(), false, knobs, frontPageData);
    }
  }

  private void flowClosedToOpen(List<ParamAdjustments> parametersList, Status newStatus,
    Pair<String, String> changeResolution, Pair<String, String> changeAssignedTo, List<String> knobs, boolean bz31,
    FrontPageData frontPageData) throws UploadException
  {
    // closed => open
    // 0. Check resolution wasn't intentionally set to non-empty
    if (changeResolution.getSecond() != null && !isEmpty(changeResolution.getSecond()) &&
      !BugzillaAttribute.RESOLUTION.getEmptyValueName().equals(changeResolution.getSecond()))
    {
      throw new BadWorkflowChange("Resolution cannot be set when reopening a bug");
    }
    if (!bz31) {
      // 1. Reopen - arrive at UNCONFIRMED or REOPEN
      // todo comment?
      setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.REOPEN, null, knobs, bz31);
      // 2. If we want "NEW" status - we access it through CONFIRM.
      if (newStatus.getStatus() == BugStatus.NEW) {
        setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.CONFIRM, null, knobs, bz31);
      }
      // 3. If we want reassign - do it now.
      if (changeAssignedTo.getSecond() != null) {
        doReassignBug(parametersList, changeAssignedTo.getSecond(), true, knobs, frontPageData);
      }
      // 4. We now at UNCONFIRMED, NEW, or REOPEN
      if (newStatus.getStatus() == BugStatus.ASSIGNED) {
        setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.ACCEPT, null, knobs, bz31);
      }
    } else {
      if (changeAssignedTo.getSecond() != null) {
        doReassignBug(parametersList, changeAssignedTo.getSecond(), true, knobs, frontPageData);
      }
      setAction(getNextWorkflowRequest(parametersList, frontPageData), null, newStatus.getName(), knobs, bz31);
    }
  }

  private void flowToClosed(List<ParamAdjustments> parametersList, Status newStatus,
    Pair<String, String> changeResolution, Pair<String, String> changeAssignedTo,
    Pair<String, String> changeDuplicateOf, List<String> knobs, boolean bz31, FrontPageData frontPageData)
    throws UploadException
  {
    // 1. Go through RESOLVED state if resolution is set.
    // 1a. If duplicate_id is changed, but the status remains duplicate - go through RESOLVED anyway.
    boolean resolved = false;
    if (changeResolution.getSecond() != null) {
      doResolveBug(parametersList, changeResolution.getSecond(), knobs, bz31, newStatus, false, false, frontPageData);
      resolved = true;
    } else if (changeDuplicateOf.getSecond() != null) {
      doResolveDuplicateBug(parametersList, changeResolution, knobs, bz31, newStatus, false, frontPageData);
      resolved = true;
    }
    // 2. Go to VERIFIED or CLOSED if that's what we want
    if ((bz31 || newStatus.getStatus() == BugStatus.RESOLVED) && !resolved) {
      String resolution = changeResolution.getFirst();
      if (isEmpty(resolution))
        throw new BadWorkflowChange("When resolving a bug, resolution must be set");
      doResolveBug(parametersList, resolution, knobs, bz31, newStatus, false, true, frontPageData);
    } else {
      finishResolved(parametersList, newStatus, bz31, frontPageData);
    }
    // 3. Change Assigned To (not allowed through default web interface)
    if (changeAssignedTo.getSecond() != null)
      doReassignBug(parametersList, changeAssignedTo.getSecond(), true, knobs, frontPageData);
  }

  private void finishResolved(List<ParamAdjustments> parametersList, Status desiredStatus, boolean bz31,
    FrontPageData frontPageData) throws UploadException
  {
    if (bz31)
      return;
    if (desiredStatus.getStatus() == BugStatus.VERIFIED)
      setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.VERIFY, null, null, false);
    else if (desiredStatus.getStatus() == BugStatus.CLOSED)
      setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.CLOSE, null, null, false);
  }

  private void doResolveDuplicateBug(List<ParamAdjustments> parametersList,
    Pair<String, String> changeResolution, List<String> knobs, boolean bz31, Status status, boolean statusNotChanging,
    FrontPageData frontPageData) throws UploadException
  {
    String oldResolution = changeResolution.getFirst();
    if (!BugzillaHTMLConstants.RESOLUTION_DUPLICATE.equalsIgnoreCase(oldResolution))
      throw new BadWorkflowChange("Cannot change DUPLICATE_OF when resolution is not DUPLICATE");
    doResolveBug(parametersList, oldResolution, knobs, bz31, status, statusNotChanging, false, frontPageData);
  }

  private void doResolveBug(List<ParamAdjustments> parametersList, String resolution, List<String> knobs,
    boolean bz31, Status status, boolean statusNotChanging, boolean resolutionOrDuplicateNotChanging,
    FrontPageData frontPageData) throws UploadException
  {
    unsupportMove(resolution);
    String duplicateOf = getDuplicateOf(myUpdateInfo, resolution);
    ParamAdjustments parameters = getNextWorkflowRequest(parametersList, frontPageData);
    if (duplicateOf != null && !(resolutionOrDuplicateNotChanging && bz31)) {
      setAction(parameters, BugzillaAction.DUPLICATE, null, knobs, bz31);
      set(parameters, BugzillaAction.DUPLICATE_DUP_ID.getName(), duplicateOf);
    } else {
      if (bz31 && statusNotChanging) {
        setAction(parameters, null, "change_resolution", knobs, bz31);
      } else {
        setAction(parameters, BugzillaAction.RESOLVE, status.getName(), knobs, bz31);
      }
      if (!(bz31 && BugzillaHTMLConstants.RESOLUTION_DUPLICATE.equalsIgnoreCase(resolution))) {
        set(parameters, BugzillaAction.RESOLVE_RESOLUTION.getName(), resolution);
        if (bz31) {
          set(parameters, OperUtils.PROPAGATE_RESOLUTIONS, OperUtils.PROPAGATE_RESOLUTIONS);
        }
      }
    }
  }

  private static String getDuplicateOf(BugInfoForUpload updateInfo, String resolution) throws BadWorkflowChange {
    String duplicateOf = null;
    if (BugzillaHTMLConstants.RESOLUTION_DUPLICATE.equalsIgnoreCase(resolution)) {
      duplicateOf = updateInfo.getAnyValue(BugzillaAttribute.DUPLICATE_OF, null);
      if (duplicateOf == null)
        throw new BadWorkflowChange("When changing resolution to " + BugzillaHTMLConstants.RESOLUTION_DUPLICATE + ", " +
          "DUPLICATE_OF field is required"); // todo rephrase
      duplicateOf = duplicateOf.trim();
      int id = -1;
      try {
        id = Integer.parseInt(duplicateOf);
      } catch (NumberFormatException e) {
        DECL.fallThrough();
      }
      if (id <= 0) {
        throw new BadWorkflowChange("Bad DUPLICATE_OF field: " + duplicateOf);
      }
    }
    return duplicateOf;
  }

  private static void unsupportMove(String resolution) throws BadWorkflowChange {
    if (BugzillaHTMLConstants.RESOLUTION_MOVED.equalsIgnoreCase(resolution))
      throw new BadWorkflowChange("Cannot change resolution to " + BugzillaHTMLConstants.RESOLUTION_MOVED);
  }

  private void flowFromOpenToOpen(List<ParamAdjustments> parametersList, Status oldStatus, Status newStatus,
    Pair<String, String> changeResolution, Pair<String, String> changeAssignedTo, List<String> knobs, boolean bz31,
    FrontPageData frontPageData) throws UploadException
  {

    if (!bz31 && (newStatus.getStatus() == BugStatus.REOPENED || newStatus.getStatus() == BugStatus.UNCONFIRMED))
      throw new BadWorkflowChange("Cannot change status from " + oldStatus + " to " + newStatus);
    if (changeResolution.getSecond() != null)
      throw new BadWorkflowChange("Cannot change resolution while bug is open");

    if (!bz31) {
      boolean reassign = false;
      if (changeAssignedTo.getSecond() != null) {
        doReassignBug(parametersList, changeAssignedTo.getSecond(), newStatus.getStatus() != BugStatus.UNCONFIRMED,
          knobs, frontPageData);
        reassign = true;
      }
      if (newStatus.getStatus() == BugStatus.NEW) {
        if (!reassign && changeAssignedTo.getFirst() != null) {
          // reassign to self - effectively changes status to NEW
          doReassignBug(parametersList, changeAssignedTo.getFirst(), true, knobs, frontPageData);
        }
      } else if (newStatus.getStatus() == BugStatus.ASSIGNED) {
        setAction(getNextWorkflowRequest(parametersList, frontPageData), BugzillaAction.ACCEPT, "ASSIGNED", knobs, bz31);
      }
    } else {
      if (changeAssignedTo.getSecond() != null) {
        doReassignBug(parametersList, changeAssignedTo.getSecond(), false, knobs, frontPageData);
      }
      setAction(getNextWorkflowRequest(parametersList, frontPageData), null, newStatus.getName(), knobs, bz31);
    }
  }

  private void doReassignBug(List<ParamAdjustments> parametersList, String assignedTo, boolean andConfirm,
    List<String> knobs, FrontPageData frontPageData) throws UploadException
  {
    ParamAdjustments parameters;
    if (knobs.contains(BugzillaAction.REASSIGN.getName())) {
      parameters = getNextWorkflowRequest(parametersList, frontPageData);
      setAction(parameters, BugzillaAction.REASSIGN, null, knobs, false);
      if (andConfirm) {
        set(parameters, BugzillaAction.REASSIGN_AND_CONFIRM.getName(), BugzillaHTMLConstants.CHECKBOX_ON);
      }
    } else {
      int count = parametersList.size();
      if (count > 0) {
        parameters = parametersList.get(count - 1);
      } else {
        parameters = getNextWorkflowRequest(parametersList, frontPageData);
      }
    }
    set(parameters, BugzillaAction.REASSIGN_EMAIL.getName(), assignedTo);
  }

  private void setAction(ParamAdjustments parameters, BugzillaAction action, String bz31action, List<String> knobs, boolean bz31) {
    // todo actual POST parameter should not depend on NAME of a public enum
    if (bz31action != null && (knobs.contains(bz31action) || bz31 || action == null)) {
      set(parameters, BugzillaHTMLConstants.UPDATE_KNOB, bz31action);
    } else {
      set(parameters, BugzillaHTMLConstants.UPDATE_KNOB,
        action == null ? BugzillaHTMLConstants.KNOB_NONE : action.getName());
    }
  }

  private ParamAdjustments getNextWorkflowRequest(List<ParamAdjustments> parametersList, FrontPageData frontPageData) throws UploadException {
    for(final ParamAdjustments parameters : parametersList) {
      final String knobValue = parameters.getReplacement(BugzillaHTMLConstants.UPDATE_KNOB);
      if(knobValue == null || BugzillaHTMLConstants.KNOB_NONE.equals(knobValue)) {
        parameters.forgetReplacement(BugzillaHTMLConstants.UPDATE_KNOB);
        return parameters;
      }
    }

    final ParamAdjustments parameters = buildRequiredParameters(frontPageData, false);
    parametersList.add(parameters);
    return parameters;
  }

  private void set(ParamAdjustments parameters, String name, String value) {
    parameters.replace(name, value);
  }

  /**
   * @return pair of (previous value, new value)
   */
  private Pair<String, String> getWorkflowAttribute(BugzillaAttribute attribute, boolean mandatory) throws BadWorkflowChange {
    String newStatus = myUpdateInfo.getNewValues().getScalarValue(attribute, null);
    String previousStatus = myUpdateInfo.getPrevValues().getScalarValue(attribute, null);
    if (!isEmpty(newStatus)) {
      if (mandatory && isEmpty(previousStatus))
        throw new BadWorkflowChange("Unable to get previous value of " + attribute.getName());
      if (Util.equals(newStatus, previousStatus))
        newStatus = null;
    }
    return Pair.create(previousStatus, newStatus);
  }

  private boolean isEmpty(String v) {
    return v == null || v.length() == 0;
  }

  private void addParametersForCC(ParamAdjustments parameters) {
    if (myUpdateInfo.getRemoveCC().size() > 0) {
      parameters.replace(BugzillaHTMLConstants.UPDATE_CC_REMOVE_FIELD2, "on"); // todo constant for "on"
      parameters.replace(BugzillaHTMLConstants.UPDATE_CC_REMOVE_FIELD1,
        OperUtils.removeEmailSuffix(myUpdateInfo.getRemoveCC(), myServerInfo.getEmailSuffix()));
    }
    if (myUpdateInfo.getAddCC().size() > 0) {
      String ccparam = CollectionUtil.implode(
        OperUtils.removeEmailSuffix(myUpdateInfo.getAddCC(), myServerInfo.getEmailSuffix()), ",");
      parameters.replace(BugzillaHTMLConstants.UPDATE_CC_ADD_FIELD, ccparam);
    }
  }

  private void addParametersForFirstComment(ParamAdjustments parameters, long lastUpdateAttemptTime) {
    String comment = "";
    Boolean privacy = null;
    BigDecimal timeWorked = null;
    List<BugInfoForUpload.CommentForUpload> comments = myUpdateInfo.getComments();
    if (comments.size() > 0) {
      // first comment, others go later
      BugInfoForUpload.CommentForUpload c = comments.get(0);
      String text = c.getComment();
      if (!isCommentAlreadySubmitted(text, lastUpdateAttemptTime)) {
        comment = OperUtils.reformatComment(c.getComment());
        privacy = c.getPrivacy();
        timeWorked = c.getTimeWorked();
      }
    }
    addComment(parameters, comment, privacy, timeWorked);
  }

  private static void addComment(ParamAdjustments parameters, String comment, Boolean privacy, BigDecimal timeWorked) {
    parameters.replace(BugzillaHTMLConstants.UPDATE_COMMENT_FIELD_NAME, comment);
    if (Boolean.TRUE.equals(privacy)) {
      parameters.replace(BugzillaHTMLConstants.UPDATE_COMMENT_PRIVACY_FIELD_NAME, "1");
      parameters.replace(BugzillaHTMLConstants.UPDATE_COMMENT_PRIVACY_FIELD_NAME_BZ40, "1");
    }
    if (timeWorked != null) {
      parameters.replace(BugzillaHTMLConstants.UPDATE_COMMENT_TIME_WORKED, timeWorked.toPlainString());
    }
  }

  private boolean isCommentAlreadySubmitted(String text, long lastUpdateAttemptTime) {
    if (lastUpdateAttemptTime <= 0)
      return false;
    BugInfo bugInfo = getBugInfo();
    if (bugInfo == null) {
      Log.warn("cannot check whether comment already submitted: " + StringUtil.limitString(text, 40));
      return false;
    }
    return OperUtils.isCommentSubmitted(text, bugInfo, lastUpdateAttemptTime, myUsername);
  }

  private BugInfo getBugInfo() {
    if (myBugInfo == null) {
      int id = Util.toInt(myId, -1);
      if (id <= 0) {
        Log.debug("bad id: " + myId);
        return null;
      }
      LoadBugsXML loader =
        new LoadBugsXML(myServerInfo, new Integer[] {id}, new Progress(), "excludefield=attachment", false, true);
      BugInfo info = null;
      try {
        Collection<BugInfo> infos = loader.loadBugs();
        if (!infos.isEmpty()) {
          info = infos.iterator().next();
        }
      } catch (ConnectorException e) {
        Log.warn("cannot get xml for " + id, e);
      }
      myBugInfo = info == null ? new BugInfo(myServerInfo.getDefaultTimezone()) : info;
    }
    return myBugInfo;
  }

  private void addParametersForScalars(ParamAdjustments parameters) throws UploadException {
    Map<BugzillaAttribute, String> scalars = myUpdateInfo.getNewValues().getScalars();
    for (Iterator<BugzillaAttribute> ii = scalars.keySet().iterator(); ii.hasNext();) {
      BugzillaAttribute attribute = ii.next();

      if (attribute == BugzillaAttribute.SEE_ALSO) {
        // can be scalar, but uploaded in another procedure
        continue;
      }

      if (BugzillaHTMLConstants.NO_UPDATE_REQUIRED_SET.contains(attribute)) {
        continue;
      }

      if (BugzillaHTMLConstants.UPDATE_ATTRIBUTE_REQUIRED_MAP.containsKey(attribute)) {
        // this attribute was already built with buildRequiredParameters
        continue;
      }

      if (UPDATE_WORKFLOW_ATTRIBUTE_MAP.containsKey(attribute)) {
        // this attribute is changed in a special way, later
        continue;
      }

      String name = BugzillaHTMLConstants.UPDATE_FORM_FIELD_NAMES_MAP.get(attribute);
      if (name == null) {
        Log.warn("cannot upload attribute " + attribute + " for #" + myId + "[" + scalars.get(attribute) + "]");
        continue;
      }

      String value = Util.NN(scalars.get(attribute));
      value = myServerInfo.removeSuffix(attribute, value);
      parameters.replace(name, value);
    }
  }

  private ParamAdjustments buildRequiredParameters(FrontPageData frontPageData, boolean insertEmptyKnob) throws UploadException {
    if (myRequiredParameters == null) {
      myRequiredParameters = new ParamAdjustments();
      myRequiredParameters.replace("longdesclength", "1");
      Set<BugzillaAttribute> requiredAttributes = BugzillaHTMLConstants.UPDATE_ATTRIBUTE_REQUIRED_MAP.keySet();
      for (BugzillaAttribute requiredAttribute : requiredAttributes) {
        if (requiredAttribute == BugzillaAttribute.DELTA_TS) {
          // precise timestamp is set by update() method for each upload action
          continue;
        }

        String value = myUpdateInfo.getAnyValue(requiredAttribute, "");
        value = myServerInfo.removeSuffix(requiredAttribute, value);
        if (value.length() == 0) {
          // todo see http://forum.almworks.com/viewtopic.php?t=247; berata.com
          value = Util.NN(BugzillaHTMLConstants.UPDATE_ATTRIBUTE_DEFAULT_VALUE_MAP.get(requiredAttribute));
        }
        String name = BugzillaHTMLConstants.UPDATE_FORM_FIELD_NAMES_MAP.get(requiredAttribute);
        if (name == null) {
          throw new Failure("code inconsistency: required attribute name is not defined [" + requiredAttribute + "]");
        }

        if (requiredAttribute.isOptional()) {
          // optional attributes - verify they are on the form
          if (!frontPageData.getFormParameters().containsKey(name)) {
            String v = Util.NN(value).trim();
            if (v.length() == 0 || "---".equals(v)) {
              Log.debug("skipping " + requiredAttribute + " when updating");
              continue;
            } else {
              Log.warn("updating optional attribute " + requiredAttribute + " that's not on the form");
            }
          }
        }
        myRequiredParameters.replace(name, value);
      }

      for(final NameValuePair pair: BugzillaHTMLConstants.ADDITIONAL_SUBMIT_UPDATE_PAIRS) {
        myRequiredParameters.replace(pair.getName(), pair.getValue());
      }
    }

    ParamAdjustments copy = myRequiredParameters.copy();
    if(insertEmptyKnob) {
      copy.replace(BugzillaHTMLConstants.UPDATE_KNOB, BugzillaHTMLConstants.KNOB_NONE);
    }
    
    return copy;
  }
}