package com.almworks.bugzilla.integration;

import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This class lists bugzilla bug attributes. The name may be used anywhere else, so be careful when changing a name.
 */
public final class BugzillaAttribute {
  public static final BugzillaAttribute ID = new BugzillaAttribute("ID", "bug id");
  public static final BugzillaAttribute ALIAS = new BugzillaAttribute("Alias");
  public static final BugzillaAttribute CREATION_TIMESTAMP =
    new BugzillaAttribute("Submit Date", "date and time of creation");
  public static final BugzillaAttribute MODIFICATION_TIMESTAMP =
    new BugzillaAttribute("Last Modified", "date and time of last modification");
  public static final BugzillaAttribute SECURITY_GROUP =
    new BugzillaAttribute("Group", "if set, only users of this group can view this bug");
  public static final BugzillaAttribute REPORTER_ACCESSIBLE =
    new BugzillaAttribute("Reporter Accessible", "if yes, bug reporter can see bug regardless other permissions");
  public static final BugzillaAttribute CCLIST_ACCESSIBLE =
    new BugzillaAttribute("CC List Accessible", "if yes, users in CC list can see bug regardless other permissions");
  public static final BugzillaAttribute SHORT_DESCRIPTION =
    new BugzillaAttribute("Summary", "short description of the bug");
  public static final BugzillaAttribute STATUS_WHITEBOARD =
    new BugzillaAttribute("Status Whiteboard", "placeholder for some text about status");
  public static final BugzillaAttribute BUG_URL =
    new BugzillaAttribute("URL", "location of something related to this bug");
  public static final BugzillaAttribute PRODUCT = new BugzillaAttribute("Product");
  public static final BugzillaAttribute COMPONENT = new BugzillaAttribute("Component");
  public static final BugzillaAttribute VERSION = new BugzillaAttribute("Version");
  public static final BugzillaAttribute TARGET_MILESTONE = new BugzillaAttribute("Target Milestone");
  public static final BugzillaAttribute PLATFORM = new BugzillaAttribute("Platform");
  public static final BugzillaAttribute OPERATING_SYSTEM = new BugzillaAttribute("Operating System");
  public static final BugzillaAttribute STATUS = new BugzillaAttribute("Status");
  public static final BugzillaAttribute RESOLUTION = new BugzillaAttribute("Resolution");
  public static final BugzillaAttribute PRIORITY = new BugzillaAttribute("Priority");
  public static final BugzillaAttribute SEVERITY = new BugzillaAttribute("Severity");
  public static final BugzillaAttribute KEYWORDS =
    new BugzillaAttribute("Keywords", "comma-delimited list of keywords");
  public static final BugzillaAttribute BLOCKED_BY = new BugzillaAttribute("Blocked By", "list of blocking bug ids");
  public static final BugzillaAttribute BLOCKS = new BugzillaAttribute("Blocks", "list of bugs blocked by this bug");
  public static final BugzillaAttribute ASSIGNED_TO =
    new BugzillaAttribute("Assigned To", "user who's currently in charge of the bug");
  public static final BugzillaAttribute REPORTER = new BugzillaAttribute("Reporter", "user who submitted the bug");
  public static final BugzillaAttribute QA_CONTACT = new BugzillaAttribute("QA Contact", "yet another user field");
  public static final BugzillaAttribute CC = new BugzillaAttribute("CC", "list of users who watch this bug");
  public static final BugzillaAttribute TOTAL_VOTES = new BugzillaAttribute("Votes", "number of votes for this bug");
  public static final BugzillaAttribute VOTER_LIST = new BugzillaAttribute("Vote list", "List of voters");
  public static final BugzillaAttribute OUR_VOTES = new BugzillaAttribute("Votes", "number of our votes for this bug");
  public static final BugzillaAttribute ESTIMATED_TIME = new BugzillaAttribute("Original Estimate");
  public static final BugzillaAttribute REMAINING_TIME = new BugzillaAttribute("Hours Left");
  public static final BugzillaAttribute ACTUAL_TIME = new BugzillaAttribute("Hours Worked");

  // Since 2.19
  public static final BugzillaAttribute CLASSIFICATION =
    new BugzillaAttribute("Classification", "Grouping of Products");
  public static final BugzillaAttribute CLASSIFICATION_ID = new BugzillaAttribute("Classification ID");

  // Since 2.20
  public static final BugzillaAttribute DEADLINE = new BugzillaAttribute("Deadline");

  // Since 2.22
  public static final BugzillaAttribute EVER_CONFIRMED =
    new BugzillaAttribute("Ever Confirmed", "1 if the bug has ever been confirmed");

  // Pseudo-attribute: duplicate id
  public static final BugzillaAttribute DUPLICATE_OF =
    new BugzillaAttribute("Duplicate Of", "Bug ID of which this bug is duplicate");

  // **EXPERIMENTAL**
  public static final BugzillaAttribute DELTA_TS =
    new BugzillaAttribute("delta_ts", "precise modification time, used in bug updates");
  public static final BugzillaAttribute MODIFICATION_AUTHOR =
    new BugzillaAttribute("changer", "user who made the change");

  // Since 3.4
  public static final BugzillaAttribute SEE_ALSO =
    new BugzillaAttribute("See Also", "References to bugs in other Bugzilla installations");


  // ***WARNING***    THE FOLLOWING LINE MUST COME AFTER ALL BugzillaAttribute INITIALIZATIONS   ***WARNING***
  public static final Map<String, BugzillaAttribute> ALL_ATTRIBUTES = createAttributesList();
  public static final List<BugzillaAttribute> PRODUCT_DEPENDENT_ATTRIBUTES =
    Collections15.list(VERSION, COMPONENT, TARGET_MILESTONE);
  public static final List<BugzillaAttribute> ATTRIBUTES_WITH_DEFAULT_VALUES =
    Collections15.list(COMPONENT, VERSION, TARGET_MILESTONE, SEVERITY, PLATFORM, PRIORITY, OPERATING_SYSTEM,
      ASSIGNED_TO, STATUS);


  public static final String RESOLUTION_DUPLICATE = "DUPLICATE";
  public static final String NO_MILESTONE = "---";
  public static final String NOT_AVAILABLE = "N/A";

  public static BugzillaAttribute getAttributeByName(String attributeName) {
    return ALL_ATTRIBUTES.get(attributeName);
  }

  private final String myName;
  private final String myDescription;

  private static Map<String, BugzillaAttribute> ourInitializationStorage;

  public BugzillaAttribute(String name, String description) {
    myName = name;
    myDescription = description;

    if (ourInitializationStorage == null)
      ourInitializationStorage = new HashMap<String, BugzillaAttribute>(20);
    ourInitializationStorage.put(myName, this);
  }

  public BugzillaAttribute(String name) {
    this(name, name);
  }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public String toString() {
    return myName;
  }

  private static Map<String, BugzillaAttribute> createAttributesList() {
    return Collections.unmodifiableMap(ourInitializationStorage);
  }

  public boolean isTuple() {
    return this == BLOCKED_BY || this == BLOCKS || this == CC || this == SECURITY_GROUP;
  }

  public boolean isLongBreakableText() {
    return this == ALIAS || this == BUG_URL || this == SHORT_DESCRIPTION || this == STATUS_WHITEBOARD;
  }

  public boolean isMultilineExport() {
    return this == SHORT_DESCRIPTION || this == STATUS_WHITEBOARD;
  }

  public boolean isDecimal() {
    return this == ACTUAL_TIME || this == REMAINING_TIME || this == ESTIMATED_TIME;
  }

  /**
   * These attirbutes are reported incrementally in history. (Meaning history contains increments.)
   */
  public boolean isCumulativelyReported() {
    return this == ACTUAL_TIME;
  }

  public String getEmptyValueName() {
    //return "<No " + English.capitalize(getName()) + ">";
    return NOT_AVAILABLE;
  }

  public String getEmptyValueName(String value) {
    //return "<No " + English.capitalize(getName()) + ">";
    if (this == RESOLUTION) {
      return NOT_AVAILABLE;
    } else {
      return value;
    }
  }

  public boolean isEmptyValue(@NotNull String value) {
    if (this != TARGET_MILESTONE && BugzillaAttribute.NO_MILESTONE.equals(value)) {
      return true;
    }
    return value.length() == 0;
  }

  public boolean isOptional() {
    return
        this == BugzillaAttribute.TARGET_MILESTONE
     || this == BugzillaAttribute.QA_CONTACT
     || this == BugzillaAttribute.STATUS_WHITEBOARD
     || this == BugzillaAttribute.ALIAS
     || this == BugzillaAttribute.SEE_ALSO
     || this == BugzillaAttribute.TOTAL_VOTES
     ;
  }

  public boolean isUser() {
    return 
         this == ASSIGNED_TO
      || this == QA_CONTACT
      || this == REPORTER
      || this == CC
      // Also: commenter
      ;
  }
}
