package com.almworks.bugzilla.integration;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.util.Env;
import org.almworks.util.*;
import org.apache.commons.httpclient.NameValuePair;
import org.jetbrains.annotations.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class BugzillaHTMLConstants {
  public static final String URL_AUTHENTICATE = "query.cgi";
  public static final String URL_BUGLIST_PREFIX = "buglist.cgi?";
  public static final String URL_QUERY_PAGE_ADVANCED = "query.cgi?format=advanced";
  public static final String URL_QUERY_PAGE_NOFORMAT = "query.cgi";
  public static final String URL_QUERY_FOR_LOAD_DICTIONARIES = "query.cgi?format=advanced&js=1&rememberjs=1";
  public static final String URL_SUBMIT_BUG = "post_bug.cgi";
  public static final String URL_TOTAL_BUG_VOTES = "votes.cgi?action=show_bug&bug_id=" ;
  public static final String URL_USER_VOTES = "votes.cgi?action=show_user&bug_id=";
  public static final String URL_VOTE_POST = "votes.cgi";
  public static final String URL_VOTE_USER_BZ40 = "voting/user.html";
  public static final String URL_VOTE_BUG_BZ40 = "voting/bug.html";

  public static final String URL_UPDATE_BUG = "process_bug.cgi";
  public static final String URL_BUG_ACTIVITY = "show_activity.cgi?id=";
  public static final String URL_FRONT_PAGE = "show_bug.cgi?id=";
  public static final String URL_LOGIN_SCREEN = "query.cgi?GoAheadAndLogIn=1";
  public static final String URL_INDEX_LOGIN_SCREEN = "index.cgi?GoAheadAndLogIn=1";
  public static final String URL_KEYWORDS_DESCRIPTION = "describekeywords.cgi";

  public static final String URL_BUG_COUNT_BY_PRODUCTS = "report.cgi?y_axis_field=product&format=table&action=wrap&ctype=csv";

  public static final String BUGLIST_COOKIE_NAME = "BUGLIST";

  public static final String CHECKBOX_ON = "on";
  public static final String RESOLUTION_MOVED = "MOVED";
  public static final String RESOLUTION_DUPLICATE = "DUPLICATE";

  public static final String STATUS_AFTER_SUBMIT = "UNCONFIRMED";

  public static final Pattern BUGS_FOUND = Pattern.compile(".*?(\\d+)\\s*bugs\\s*found.*",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  public static final String BUG_MARKER = "show_bug.cgi?id=";
  public static final String SHOW_ATTACHMENT_SCRIPT = "attachment.cgi?id=$id$&action=edit";
  public static final String DOWNLOAD_ATTACHMENT_SCRIPT = "attachment.cgi?id=$id$";
  public static final String URL_CREATE_ATTACHMENT = "attachment.cgi?bugid=$id$&action=enter";
  public static final String URL_UPLOAD_ATTACHMENT = "attachment.cgi";
  public static final String GO_AHEAD_AND_LOGIN = "?GoAheadAndLogIn=1";
  public static final String URL_RELOGIN = "relogin.cgi";
  public static final String URL_RELOGIN_2 = "index.cgi?logout=1";
  public static final String COOKIE_BUGZILLA_LOGIN = "BUGZILLA_LOGIN";
  public static final String COOKIE_BUGZILLA_LOGINCOOKIE = "BUGZILLA_LOGINCOOKIE";
  public static final String URL_ENTER_BUG = "enter_bug.cgi?product=";
  public static final Pattern CREATED_AN_ATTACHMENT_COMMENT = Pattern.compile(".*\\s\\(id\\=(\\d+)\\)$");
  public static final String GROUP_ID_PREFIX = "bit-";
  public static final String GROUP_ID_PREFIX_BZ40 = "group_";

  public static interface BZ_2_18 {
    public static final String URL_BUGS_LOADXML_PREFIX = "show_bug.cgi?ctype=xml"; // appended &id=
    public static final String URL_BUGS_LOADXML_REPETITIVE_ID_PART = "&id=$id";
  }

  public static final String URL_BUGS_LOADXML_PREFIX = "xml.cgi?id="; // appended &id=
  public static final String URL_BUGS_LOADXML_REPETITIVE_ID_PART = "$id";
  public static final String URL_BUGS_LOADXML_REPETITIVE_BETWEEN_IDS = "%2C";

  public static final String URL_ID_TEMPLATE = "\\$id";
  public static final Pattern URL_ID_EXRACTOR = Pattern.compile("^show_bug\\.cgi\\?id\\=(\\d+)$");
  public static final int BUG_FIELDS_URL_MAX_IDS = Env.getInteger("bugzilla.xml.maxload", 100);
  public static final int PAGELOAD_MINIMUM_DELAY = Env.getInteger("bugzilla.xml.req.mindelay", 0, 60, 0);
  public static final int BUGZILLA_COMMENT_LINE = Env.getInteger("bugzilla.comment.line", 80);
  public static final boolean BUGZILLA_COMMENT_NOWRAP = Env.getBoolean("bugzilla.comment.nowrap", false);

  public static final String XML_TAG_BUG = "bug";
  public static final String XML_TAG_VERSION = "version";
  public static final String XML_TAG_BUG_COMMENT = "long_desc";
  public static final String XML_TAG_BUG_COMMENT_WHO = "who";
  public static final String XML_TAG_BUG_COMMENT_WHEN = "bug_when";
  public static final String XML_TAG_BUG_COMMENT_TEXT = "thetext";
  public static final String XML_TAG_BUG_COMMENT_WORK_TIME = "work_time";
  public static final String XML_TAG_BUG_ATTACHMENT = "attachment";
  public static final String XML_TAG_BUG_ATTACHMENT_ID = "attachid";
  public static final String XML_TAG_BUG_ATTACHMENT_DATE = "date";
  public static final String XML_TAG_BUG_ATTACHMENT_DESCRIPTION = "desc";
  public static final String XML_TAG_BUG_COMMENT_ISPRIVATE = "isprivate";
  public static final String XML_TAG_FLAG = "flag";

  public static final String XML_ATTRIBUTE_BUG_ERROR = "error";

  public static final String BUGS_TABLE_CSS_CLASS = "bz_buglist";
  public static final String ID_COLUMN_CSS_CLASS = "bz_id_column";

  private static final Object[][] CSS_CLASS_TO_ATTRIBUTE_DATA = {
    {BugzillaAttribute.ID, ID_COLUMN_CSS_CLASS, },
    {BugzillaAttribute.SEVERITY, "bz_bug_severity_column", "bz_severity_column", }, // this demonstrates that there may be several classes for one column
    {BugzillaAttribute.PRIORITY, "bz_priority_column", },
    {BugzillaAttribute.PLATFORM, "bz_rep_platform_column", "bz_platform_column", },
    {BugzillaAttribute.ASSIGNED_TO, "bz_assigned_to_column", "bz_owner_column", },
    {BugzillaAttribute.STATUS, "bz_bug_status_column", "bz_status_column", },
    {BugzillaAttribute.RESOLUTION, "bz_resolution_column", },
    {BugzillaAttribute.SHORT_DESCRIPTION, "bz_short_short_desc_column", "bz_summary_column", },
    {BugzillaAttribute.MODIFICATION_TIMESTAMP, "bz_changeddate_column", },
  };
  public static final Map<String, BugzillaAttribute> CSS_CLASS_TO_ATTRIBUTE_MAP = createBackwardMap(
    CSS_CLASS_TO_ATTRIBUTE_DATA);

  private static final Object[][] XML_TAG_TO_ATTRIBUTE_DATA = {
    {BugzillaAttribute.ID, "bug_id", },
    {BugzillaAttribute.ALIAS, "alias", },
    {BugzillaAttribute.CREATION_TIMESTAMP, "creation_ts", },
    {BugzillaAttribute.SHORT_DESCRIPTION, "short_desc", },
    {BugzillaAttribute.MODIFICATION_TIMESTAMP, "delta_ts", },
    {BugzillaAttribute.REPORTER_ACCESSIBLE, "reporter_accessible", },
    {BugzillaAttribute.CCLIST_ACCESSIBLE, "cclist_accessible", },
    {BugzillaAttribute.PRODUCT, "product", },
    {BugzillaAttribute.COMPONENT, "component", },
    {BugzillaAttribute.VERSION, "version", },
    {BugzillaAttribute.PLATFORM, "rep_platform", },
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys", },
    {BugzillaAttribute.STATUS, "bug_status", },
    {BugzillaAttribute.RESOLUTION, "resolution", },
    {BugzillaAttribute.BUG_URL, "bug_file_loc", },
    {BugzillaAttribute.STATUS_WHITEBOARD, "status_whiteboard", },
    {BugzillaAttribute.KEYWORDS, "keywords", },
    {BugzillaAttribute.PRIORITY, "priority", },
    {BugzillaAttribute.SEVERITY, "bug_severity", },
    {BugzillaAttribute.TARGET_MILESTONE, "target_milestone", },
    {BugzillaAttribute.BLOCKED_BY, "dependson", },
    {BugzillaAttribute.BLOCKS, "blocked", "blocks", },
    {BugzillaAttribute.REPORTER, "reporter", },
    {BugzillaAttribute.ASSIGNED_TO, "assigned_to", },
    {BugzillaAttribute.CC, "cc", },
    {BugzillaAttribute.QA_CONTACT, "qa_contact", },
    {BugzillaAttribute.TOTAL_VOTES, "votes", },
    {BugzillaAttribute.ESTIMATED_TIME, "estimated_time", },
    {BugzillaAttribute.REMAINING_TIME, "remaining_time", },
    {BugzillaAttribute.ACTUAL_TIME, "actual_time", },
    {BugzillaAttribute.SECURITY_GROUP, "group", },
    {BugzillaAttribute.CLASSIFICATION, "classification", },
    {BugzillaAttribute.CLASSIFICATION_ID, "classification_id", },
    {BugzillaAttribute.DEADLINE, "deadline", },
    {BugzillaAttribute.EVER_CONFIRMED, "everconfirmed"},
    {BugzillaAttribute.DUPLICATE_OF, "dup_id"},
    {BugzillaAttribute.SEE_ALSO, "see_also"},
  };

  private static final Object[][] ATTRIBUTE_TO_BOOLEAN_CHART_NAMES_DATA = {
    {BugzillaAttribute.ID, "bug_id"},
    {BugzillaAttribute.ALIAS, "alias"},
    {BugzillaAttribute.CREATION_TIMESTAMP, "creation_ts"},
    {BugzillaAttribute.SHORT_DESCRIPTION, "short_desc"},
    {BugzillaAttribute.MODIFICATION_TIMESTAMP, "delta_ts"},
    {BugzillaAttribute.REPORTER_ACCESSIBLE, "reporter_accessible"},
    {BugzillaAttribute.CCLIST_ACCESSIBLE, "cclist_accessible"},
    {BugzillaAttribute.PRODUCT, "product"},
    {BugzillaAttribute.COMPONENT, "component"},
    {BugzillaAttribute.VERSION, "version"},
    {BugzillaAttribute.PLATFORM, "rep_platform"},
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys"},
    {BugzillaAttribute.STATUS, "bug_status"},
    {BugzillaAttribute.RESOLUTION, "resolution"},
    {BugzillaAttribute.BUG_URL, "bug_file_loc"},
    {BugzillaAttribute.STATUS_WHITEBOARD, "status_whiteboard"},
    {BugzillaAttribute.KEYWORDS, "keywords"},
    {BugzillaAttribute.PRIORITY, "priority"},
    {BugzillaAttribute.SEVERITY, "bug_severity"},
    {BugzillaAttribute.TARGET_MILESTONE, "target_milestone"},
    {BugzillaAttribute.BLOCKED_BY, "dependson"},
    {BugzillaAttribute.BLOCKS, "blocked"},
    {BugzillaAttribute.REPORTER, "reporter"},
    {BugzillaAttribute.ASSIGNED_TO, "assigned_to"},
    {BugzillaAttribute.CC, "cc"},
    {BugzillaAttribute.QA_CONTACT, "qa_contact"},
    {BugzillaAttribute.TOTAL_VOTES, "votes"},
    {BugzillaAttribute.ESTIMATED_TIME, "estimated_time"},
    {BugzillaAttribute.REMAINING_TIME, "remaining_time"},
    {BugzillaAttribute.ACTUAL_TIME, "work_time"},
    {BugzillaAttribute.SECURITY_GROUP, "bug_group"},
    {BugzillaAttribute.CLASSIFICATION, "classification"},
    {BugzillaAttribute.CLASSIFICATION_ID, "classification_id"},
    {BugzillaAttribute.DEADLINE, "deadline"},
    {BugzillaAttribute.EVER_CONFIRMED, "everconfirmed"},
  };
  public static final Map<String, BugzillaAttribute> XML_TAG_TO_ATTRIBUTE_MAP =
    createBackwardMap(XML_TAG_TO_ATTRIBUTE_DATA);

  public static final String BOOLEAN_CHART_COMMENTS_SPECIAL = "longdesc";


  public static final Map<BugzillaAttribute, String> ATTRIBUTE_TO_BOOLEAN_CHART_MAP =
    createForwardMap(ATTRIBUTE_TO_BOOLEAN_CHART_NAMES_DATA);

  private static final Object[][] HTML_SELECTION_NAME_TO_ENUMERATION_ATTRIBUTE = {
    {BugzillaAttribute.PRODUCT, "product", },
    {BugzillaAttribute.COMPONENT, "component", },
    {BugzillaAttribute.VERSION, "version", },
    {BugzillaAttribute.PLATFORM, "rep_platform", },
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys", },
    {BugzillaAttribute.STATUS, "bug_status", },
    {BugzillaAttribute.RESOLUTION, "resolution", },
    {BugzillaAttribute.PRIORITY, "priority", },
    {BugzillaAttribute.SEVERITY, "bug_severity", },
    {BugzillaAttribute.TARGET_MILESTONE, "target_milestone", },
    {BugzillaAttribute.CLASSIFICATION, "classification", },
    {BugzillaAttribute.ASSIGNED_TO, "assigned_to", },
  };
  public static final Map<String, BugzillaAttribute> HTML_SELECTION_NAME_ATTRIBUTE_MAP =
    createBackwardMap(HTML_SELECTION_NAME_TO_ENUMERATION_ATTRIBUTE);
  public static final Map<BugzillaAttribute, String> HTML_ATTRIBUTE_SELECTION_NAME_MAP =
    createForwardMap(HTML_SELECTION_NAME_TO_ENUMERATION_ATTRIBUTE);

  // This array should contain the minimum required set of fields to be submitted
  // to Bugzilla. See bug http://bugzilla/main/show_bug.cgi?id=871
  private static final Object[][] MANDATORY_SUBMIT_FIELDS = {
    {BugzillaAttribute.PRODUCT, "product"},                 //+
    {BugzillaAttribute.ASSIGNED_TO, "assigned_to"},         //+
    {BugzillaAttribute.CC, "cc"},                           //+
    {BugzillaAttribute.BUG_URL, "bug_file_loc"},            //+
    {BugzillaAttribute.SHORT_DESCRIPTION, "short_desc"},    //+
    {BugzillaAttribute.VERSION, "version"},                 //+
    {BugzillaAttribute.COMPONENT, "component"},             //+
    {BugzillaAttribute.PLATFORM, "rep_platform"},           //+
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys"},         //+
    {BugzillaAttribute.PRIORITY, "priority"},               //+
    {BugzillaAttribute.SEVERITY, "bug_severity"},           //+
    {BugzillaAttribute.QA_CONTACT, "qa_contact"},           //+
    {BugzillaAttribute.TARGET_MILESTONE, "target_milestone"},  //+
  };

  public static final Map<BugzillaAttribute, String> MANDATORY_SUBMIT_FIELDS_MAP =
    createForwardMap(MANDATORY_SUBMIT_FIELDS);

  private static final Object[][] OPTIONAL_SUBMIT_FIELDS = {
    {BugzillaAttribute.STATUS, "bug_status"},
    {BugzillaAttribute.ESTIMATED_TIME, "estimated_time"},
    {BugzillaAttribute.DEADLINE, "deadline"},
    {BugzillaAttribute.BLOCKED_BY, "dependson"},
    {BugzillaAttribute.BLOCKS, "blocked"},
    {BugzillaAttribute.KEYWORDS, "keywords"},
  };

  public static final Map<BugzillaAttribute, String> OPTIONAL_SUBMIT_FIELDS_MAP =
    createForwardMap(OPTIONAL_SUBMIT_FIELDS);


  // captured from v 2.18
  private static final Object[][] UPDATE_FORM_FIELD_NAMES = {
    {BugzillaAttribute.ALIAS, "alias"},
    {BugzillaAttribute.BLOCKED_BY, "dependson"},
    {BugzillaAttribute.BLOCKS, "blocked"},
    {BugzillaAttribute.BUG_URL, "bug_file_loc"},
/*
    {BugzillaAttribute.CCLIST_ACCESSIBLE, "cclist_accessible"},
*/
    {BugzillaAttribute.COMPONENT, "component"},
    {BugzillaAttribute.ESTIMATED_TIME, "estimated_time"},
    {BugzillaAttribute.ID, "id"},
    {BugzillaAttribute.KEYWORDS, "keywords"},
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys"},
    {BugzillaAttribute.PLATFORM, "rep_platform"},
    {BugzillaAttribute.DELTA_TS, "delta_ts"},
    {BugzillaAttribute.PRIORITY, "priority"},
    {BugzillaAttribute.PRODUCT, "product"},
    {BugzillaAttribute.QA_CONTACT, "qa_contact"},
    {BugzillaAttribute.REMAINING_TIME, "remaining_time"},
/*
    {BugzillaAttribute.REPORTER_ACCESSIBLE, "reporter_accessible"},
*/
    {BugzillaAttribute.SEVERITY, "bug_severity"},
    {BugzillaAttribute.SHORT_DESCRIPTION, "short_desc"},
    {BugzillaAttribute.STATUS_WHITEBOARD, "status_whiteboard"},
    {BugzillaAttribute.TARGET_MILESTONE, "target_milestone"},
    {BugzillaAttribute.VERSION, "version"},

    // since 2.20
    {BugzillaAttribute.DEADLINE, "deadline"},
  };

  public static final Map<BugzillaAttribute, String> UPDATE_FORM_FIELD_NAMES_MAP =
    createForwardMap(UPDATE_FORM_FIELD_NAMES);

  public static final NameValuePair[] ADDITIONAL_SUBMIT_UPDATE_PAIRS = {
    new NameValuePair("form_name", "enter_bug"),
    new NameValuePair("addtonewgroup", "yesifinold"), // since 2.19
  };

  public static final String SUBMIT_COMMENT_PARAMETER_NAME = "comment";
  public static final String SUBMIT_COMMENT_PRIVACY_PARAMETER_NAME = "commentprivacy";
  public static final String SUBMIT_COMMENT_PRIVACY_PARAMETER_NAME_BZ40 = "comment_is_private";

  public static final String SUBMIT_CC_LIST_DELIMITER = " ";

  public static final String UPDATE_ID_PARAMETER = "id";
  public static final String UPDATE_DELTA_TS_PARAMETER = "delta_ts";
  public static final String UPDATE_COMMENT_FIELD_NAME = "comment";
  public static final String UPDATE_COMMENT_PRIVACY_FIELD_NAME = "commentprivacy";
  public static final String UPDATE_COMMENT_PRIVACY_FIELD_NAME_BZ40 = "comment_is_private";
  public static final String UPDATE_COMMENT_TIME_WORKED = "work_time";

  public static final String UPDATE_CC_ADD_FIELD = "newcc";
  public static final String UPDATE_CC_REMOVE_FIELD1 = "cc";
  public static final String UPDATE_CC_REMOVE_FIELD2 = "removecc";

  public static final String UPDATE_DUPLICATE_ID_FIELD = "dup_id";

  public static final String UPDATE_KNOB = "knob";

  public static final String UPDATE_KEYWORDACTION_FIELD = "keywordaction";
  public static final String UPDATE_KEYWORDACTION_MAKEEXACT = "makeexact";
  public static final String UPDATE_KEYWORDACTION_ADD = "add";
  public static final String UPDATE_KEYWORDACTION_DELETE = "delete";

  public static final String BUG_ACTIVITY_TABLE_HEADER_WHAT = "what";
  public static final String BUG_ACTIVITY_TABLE_HEADER_WHO = "who";
  public static final String BUG_ACTIVITY_TABLE_HEADER_WHEN = "when";
  public static final String BUG_ACTIVITY_TABLE_HEADER_REMOVED = "removed";
  public static final String BUG_ACTIVITY_TABLE_HEADER_ADDED = "added";

  public static final Object[][] BUG_ACTIVITY_WHAT_ATTRIBUTE = {
    {BugzillaAttribute.ACTUAL_TIME, "Hours Worked", },
    {BugzillaAttribute.ALIAS, "Alias", },
    {BugzillaAttribute.ASSIGNED_TO, "AssignedTo", },
    {BugzillaAttribute.BLOCKS, "OtherBugsDependingOnThis", "blocked"},
    {BugzillaAttribute.BUG_URL, "URL", "bug_file_loc", },
    {BugzillaAttribute.CC, "CC", "cc", },
    {BugzillaAttribute.CCLIST_ACCESSIBLE, "CC Accessible", },
    {BugzillaAttribute.COMPONENT, "Component", "component", },
    {BugzillaAttribute.PRIORITY, "Priority", "priority", },
    {BugzillaAttribute.SEVERITY, "Severity", "bug_severity", },
    {BugzillaAttribute.PRODUCT, "Product", },
    {BugzillaAttribute.REPORTER_ACCESSIBLE, "Reporter Accessible", },
    {BugzillaAttribute.SHORT_DESCRIPTION, "Summary", "short_desc", },
    {BugzillaAttribute.TARGET_MILESTONE, "Target Milestone", "target_milestone", },
    {BugzillaAttribute.VERSION, "Version", "version", },
    {BugzillaAttribute.SECURITY_GROUP, "Group", },
    {BugzillaAttribute.BLOCKED_BY, "BugsThisDependsOn", "dependson", },
    {BugzillaAttribute.ESTIMATED_TIME, "Estimated Hours", },
    {BugzillaAttribute.KEYWORDS, "Keywords", "keywords", },
    {BugzillaAttribute.QA_CONTACT, "QAContact", "qa_contact", },
    {BugzillaAttribute.REMAINING_TIME, "Remaining Hours", },
    {BugzillaAttribute.STATUS_WHITEBOARD, "Status Whiteboard", "status_whiteboard", },
    {BugzillaAttribute.OPERATING_SYSTEM, "OS/Version", "op_sys", },
    {BugzillaAttribute.PLATFORM, "Platform", "rep_platform", },
    {BugzillaAttribute.RESOLUTION, "Resolution", "resolution", },
    {BugzillaAttribute.STATUS, "Status", "bug_status", },
    {BugzillaAttribute.DEADLINE, "Deadline", },
    {BugzillaAttribute.EVER_CONFIRMED, "Ever Confirmed", },
  };

  public static final Map<String, BugzillaAttribute> BUG_ACTIVITY_WHAT_ATTRIBUTE_MAP =
    createBackwardMap(BUG_ACTIVITY_WHAT_ATTRIBUTE);

  public static final Object[][] STANDARD_FIELD_IDS_IN_DEPENDENCIES = {
    {BugzillaAttribute.CLASSIFICATION, "classification", },
    {BugzillaAttribute.PRODUCT, "product", },
    {BugzillaAttribute.COMPONENT, "component", },
    {BugzillaAttribute.PLATFORM, "rep_platform", },
    {BugzillaAttribute.OPERATING_SYSTEM, "op_sys", },
    {BugzillaAttribute.STATUS, "bug_status", },
    {BugzillaAttribute.PRIORITY, "priority", },
    {BugzillaAttribute.SEVERITY, "bug_severity", },
    {BugzillaAttribute.RESOLUTION, "resolution", },
  };

  public static final Map<String, BugzillaAttribute> STANDARD_FIELD_IDS_IN_DEPENDENCIES_MAP =
    createBackwardMap(STANDARD_FIELD_IDS_IN_DEPENDENCIES);

  public static final String TRUE = "true";
  public static final String FALSE = "false";

  public static final BugzillaAttribute[] NO_UPDATE_REQUIRED = {
    BugzillaAttribute.TOTAL_VOTES
  };

  public static final Set<BugzillaAttribute> NO_UPDATE_REQUIRED_SET =
    Collections15.hashSet(NO_UPDATE_REQUIRED);

  public static final Object[][] UPDATE_ATTRIBUTE_REQUIRED = {
    //Attribute -> "non-empty" requirement
    {BugzillaAttribute.ID, TRUE},
    {BugzillaAttribute.DELTA_TS, TRUE},
    {BugzillaAttribute.PRODUCT, TRUE},
    {BugzillaAttribute.VERSION, TRUE},
    {BugzillaAttribute.COMPONENT, TRUE},
    {BugzillaAttribute.TARGET_MILESTONE, FALSE},
    {BugzillaAttribute.PLATFORM, TRUE},
    {BugzillaAttribute.PRIORITY, TRUE},
    {BugzillaAttribute.SEVERITY, TRUE},
    {BugzillaAttribute.BUG_URL, FALSE},
    {BugzillaAttribute.SHORT_DESCRIPTION, TRUE},
    {BugzillaAttribute.OPERATING_SYSTEM, TRUE},
/*
    {BugzillaAttribute.CCLIST_ACCESSIBLE, TRUE},
    {BugzillaAttribute.REPORTER_ACCESSIBLE, TRUE},
*/
  };
  public static final Map<BugzillaAttribute, String> UPDATE_ATTRIBUTE_REQUIRED_MAP =
    createForwardMap(UPDATE_ATTRIBUTE_REQUIRED);

  public static final Object[][] UPDATE_ATTRIBUTE_DEFAULT_VALUE = {
    {BugzillaAttribute.BUG_URL, "http://"},
  };
  public static final Map<BugzillaAttribute, String> UPDATE_ATTRIBUTE_DEFAULT_VALUE_MAP =
    Collections15.emptyMap();
//    createForwardMap(UPDATE_ATTRIBUTE_DEFAULT_VALUE);

  public static final Object[][] UPDATE_WORKFLOW_ATTRIBUTE = {
    {BugzillaAttribute.STATUS, "bug_status"},
    {BugzillaAttribute.RESOLUTION, "resolution"},
    {BugzillaAttribute.ASSIGNED_TO, "assigned_to"},
    {BugzillaAttribute.DUPLICATE_OF, "duplicate_of"},
  };
  public static final Map<BugzillaAttribute, String> UPDATE_WORKFLOW_ATTRIBUTE_MAP =
    createForwardMap(UPDATE_WORKFLOW_ATTRIBUTE);


  public static final String KNOB_NONE = "none";
  public static final String KNOB_CONFIRM = "confirm";
  public static final String KNOB_ACCEPT = "accept";
  public static final String KNOB_CLEARRESOLUTION = "clearresolution";
  public static final String KNOB_RESOLVE = "resolve";
  public static final String KNOB_REASSIGN = "reassign";
  public static final String KNOB_REASSIGNBYCOMPONENT = "reassignbycomponent";
  public static final String KNOB_REOPEN = "reopen";
  public static final String KNOB_VERIFY = "verify";
  public static final String KNOB_CLOSE = "close";
  public static final String KNOB_DUPLICATE = "duplicate";

  public static final String[] KNOBS = {
    KNOB_NONE,
    KNOB_CONFIRM,
    KNOB_ACCEPT,
    KNOB_CLEARRESOLUTION,
    KNOB_RESOLVE,
    KNOB_REASSIGN,
    KNOB_REASSIGNBYCOMPONENT,
    KNOB_REOPEN,
    KNOB_VERIFY,
    KNOB_CLOSE,
    KNOB_DUPLICATE,
  };

  private static Map<BugzillaAttribute, String> createForwardMap(Object[][] specialArray) {
    Map<BugzillaAttribute, String> map = Collections15.hashMap();
    for (int ai = 0; ai < specialArray.length; ai++) {
      assert specialArray[ai].length == 2;
      BugzillaAttribute attribute = (BugzillaAttribute) specialArray[ai][0];
      map.put(attribute, (String) specialArray[ai][1]);
    }
    return Collections.unmodifiableMap(map);
  }

  private static Map<String, BugzillaAttribute> createBackwardMap(Object[][] specialArray) {
    Map<String, BugzillaAttribute> map = Collections15.hashMap();
    for (int ai = 0; ai < specialArray.length; ai++) {
      BugzillaAttribute attribute = (BugzillaAttribute) specialArray[ai][0];
      for (int ci = 1; ci < specialArray[ai].length; ci++)
        map.put((String) specialArray[ai][ci], attribute);
    }
    return Collections.unmodifiableMap(map);
  }

  public static BugInfo.ErrorType getErrorType(String errorAttribute) {
    if ("NotPermitted".equalsIgnoreCase(errorAttribute))
      return BugInfo.ErrorType.BUG_ACCESS_DENIED;
    else if ("NotFound".equalsIgnoreCase(errorAttribute))
      return BugInfo.ErrorType.BUG_NOT_FOUND;
    else
      return null;
  }

  public static boolean isVoteAction(String action) {
    return action != null
      && (action.contains(URL_VOTE_POST)
        || action.contains(URL_VOTE_USER_BZ40)
        || action.contains(URL_VOTE_BUG_BZ40));
  }

  public static boolean isValidGroupFormId(String id) {
    return id != null && (id.startsWith(GROUP_ID_PREFIX) || id.startsWith(GROUP_ID_PREFIX_BZ40));
  }

  public static String getAlternativeFormId(String id) {
    if(id != null) {
      if(id.startsWith(GROUP_ID_PREFIX)) {
        return GROUP_ID_PREFIX_BZ40 + id.substring(GROUP_ID_PREFIX.length());
      } else if(id.startsWith(GROUP_ID_PREFIX_BZ40)) {
        return GROUP_ID_PREFIX + id.substring(GROUP_ID_PREFIX_BZ40.length());
      }
    }
    return null;
  }
  
  private static final String DEFAULT_REQUEST_DATE_FORMAT = "yyyy-MM-dd'%20'HH'%3A'mm'%3A'ss'%20'z";
  private static final String DEFAULT_REQUEST_DATE_ZONE = "GMT";
  public static DateFormat getRequestDateFormat(@Nullable String defaultTimeZone) {
    if (defaultTimeZone == null) defaultTimeZone = DEFAULT_REQUEST_DATE_ZONE;
    String formatStr = Util.NN(Env.getString("bugzilla.request.date.format", "")).trim();
    if (formatStr.isEmpty()) formatStr = DEFAULT_REQUEST_DATE_FORMAT;
    SimpleDateFormat format;
    try {
      format = new SimpleDateFormat(formatStr);
    } catch (IllegalArgumentException e) {
      Log.error(e);
      format = new SimpleDateFormat(DEFAULT_REQUEST_DATE_FORMAT);
    }
    String zoneStr = Util.NN(Env.getString("bugzilla.request.date.zone", defaultTimeZone)).trim();
    if (zoneStr.isEmpty()) zoneStr = defaultTimeZone;
    format.setTimeZone(TimeZone.getTimeZone(zoneStr));
    return format;
  }
}