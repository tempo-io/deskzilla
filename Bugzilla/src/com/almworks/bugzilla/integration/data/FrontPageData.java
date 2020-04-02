package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class FrontPageData {
  public static final int COMMENT_PRIVACY_FORMAT_BUGZILLA_310_OR_UNKNOWN = 0;
  public static final int COMMENT_PRIVACY_FORMAT_BUGZILLA_32 = 1;

  private final String myDeltaTs;
  private final List<Pair<BugGroupData, Boolean>> myGroups;
  private final MultiMap<String, String> myFormParameters;

  /**
   * Comment sequence, Comment text, private/no private
   * if null -- no privacy information available
   */
  @Nullable
  private final List<CommentInfo> myCommentPrivacyInfo;

  private final int myCommentPrivacyFormat;

  /**
   * map customfield_id => info
   */
  @Nullable
  private final Map<String, CustomFieldInfo> myCustomFieldInfo;

  /**
   * map customfield_id => value
   */
  @Nullable
  private final MultiMap<String, String> myCustomFieldValues;

  private final List<String> myKnobs;

  // bugzilla 3.1+
  /**
   * Contains list of allowed status changes for the current status of this bug.
   * Each status is also detected as open or closed.
   * If null, not available
   */
  @Nullable
  private final List<StatusInfo> myAllowedStatusChanges;

  /**
   * Contains target status for the action "mark as duplicate". Resolution is hard-coded to
   * be "DUPLICATE".
   */
  @Nullable
  private final String myMarkDuplicateStatus;

  @Nullable
  private final Integer myVoteCount;
  private final boolean myVoteLinkFound;

  private final boolean mySeeAlsoEnabled;
  private final List<String> myCurrentSeeAlso;

  @Nullable
  private final CustomFieldDependencies myCustomFieldDependencies;
  @Nullable
  private final List<FlagInfo> myFlags;


  public FrontPageData(String deltaTs, MultiMap<String, String> formParameters,
    List<Pair<BugGroupData, Boolean>> groups, List<CommentInfo> commentPrivacyInfo,
    Map<String, CustomFieldInfo> customFieldInfo, MultiMap<String, String> customFieldValues, List<String> knobs,
    List<StatusInfo> allowedStatusChanges, String markDuplicateStatus, Integer voteCount, int commentPrivacyFormat,
    boolean seeAlsoEnabled, List<String> currentSeeAlso, CustomFieldDependencies customFieldDependencies,
    List<FlagInfo> flags, boolean voteLinkFound)
  {
    myDeltaTs = deltaTs;
    myGroups = groups;
    myCommentPrivacyInfo = commentPrivacyInfo;
    myCustomFieldInfo = customFieldInfo;
    myCustomFieldValues = customFieldValues;
    myCommentPrivacyFormat = commentPrivacyFormat;
    mySeeAlsoEnabled = seeAlsoEnabled;
    myCurrentSeeAlso = currentSeeAlso;
    myCustomFieldDependencies = customFieldDependencies;
    myFlags = flags;
    myKnobs = Collections.unmodifiableList(knobs);
    if (formParameters == null) {
      myFormParameters = MultiMap.create();
    } else {
      myFormParameters = MultiMap.create(formParameters);
    }
    myAllowedStatusChanges =
      allowedStatusChanges == null ? null : Collections15.unmodifiableListCopy(allowedStatusChanges);
    myMarkDuplicateStatus = markDuplicateStatus;
    myVoteCount = voteCount;
    myVoteLinkFound = voteLinkFound;
  }

  @Nullable
  public List<FlagInfo> getFlags() {
    return myFlags;
  }

  @Nullable
  public CustomFieldDependencies getCustomFieldDependencies() {
    return myCustomFieldDependencies;
  }

  public int getCommentPrivacyFormat() {
    return myCommentPrivacyFormat;
  }

  public boolean isSeeAlsoEnabled() {
    return mySeeAlsoEnabled;
  }

  @Nullable
  public List<String> getCurrentSeeAlso() {
    return myCurrentSeeAlso;
  }

  @Nullable
  public List<StatusInfo> getAllowedStatusChanges() {
    return myAllowedStatusChanges;
  }

  @Nullable
  public String getMarkDuplicateStatus() {
    return myMarkDuplicateStatus;
  }

  public List<String> getKnobs() {
    return myKnobs;
  }

  public String getDeltaTs() {
    return myDeltaTs;
  }

  @NotNull
  public MultiMap<String, String> getFormParameters() {
    return myFormParameters;
  }

  @NotNull
  public List<Pair<BugGroupData, Boolean>> getGroups() {
    return myGroups;
  }

  @Nullable
  public List<CommentInfo> getCommentPrivacyInfo() {
    return myCommentPrivacyInfo;
  }

  @Nullable
  public Map<String, CustomFieldInfo> getCustomFieldInfo() {
    return myCustomFieldInfo;
  }

  @Nullable
  public MultiMap<String, String> getCustomFieldValues() {
    return myCustomFieldValues;
  }

  @Nullable
  public Integer getVoteCount() {
    return myVoteCount;
  }

  public boolean isVoteLinkFound() {
    return myVoteLinkFound;
  }

  public boolean isTimeTrackingPresent() {
    return myFormParameters.containsKey("work_time");
  }

  public static class CommentInfo {
    public final int sequence;
    public final int commentId;
    public final String text;
    public final long date;

    /**
     * if null -- no privacy info
     */
    public final Boolean privacy;

    private int myTextHash;

    public CommentInfo(int sequence, int commentNumber, String text, long date, Boolean privacy) {
      this.sequence = sequence;
      commentId = commentNumber;
      this.text = text;
      this.date = date;
      this.privacy = privacy;
    }

    public String toString() {
      return sequence + ":" + commentId + ":" + new Date(date) + ":" + privacy + ":" + text;
    }

    public int getTextHash() {
      if (myTextHash == 0) {
        myTextHash = BugzillaUtil.toleratingCommentHash(text);
      }
      return myTextHash;
    }
  }


  /**
   * Represent info about flag or flag type. If {@link #myId} is set this is flag info, otherwise (@link #myTypeId} is set
   * and the object represents flag type info. Note that no relation flagId to typeId is available, the relation can be
   * obtained from XML ({@link BugInfo.Flag}) when provided by Bugzilla server (version 3.0.2 does not and version 3.4 does).<br>
   * For flag types status value is always 0, for flags it is always actual value (X, +, -, ?).<br>
   * Additional properties:<br>
   * {@link #myDescription} is flag/type description, always available.
   * {@link #myAllStatuses} is all status that the current user may set. Always available.
   * Base properties:
   * {@link #myStatus}, {@link #myRequestee} are available for flags. {@link #mySetter} isnt available, should be
   * obtained from XML.
   */
  public static class FlagInfo extends BugInfo.Flag {
    private final String myDescription;
    private final char[] myAllStatuses;
    private final Boolean myAllowsRequestee;

    public FlagInfo(int flagId, char status, @NotNull List<Character> allStatuses, String name, String description,
      BugzillaUser requestee, Boolean allowsRequestee)
    {
      super(flagId, -1, name, status, null, requestee);
      myDescription = description;
      myAllStatuses = toArray(allStatuses);
      myAllowsRequestee = allowsRequestee;
    }

    public FlagInfo(int typeId, List<Character> allStatuses, String name, String description, Boolean allowsRequestee) {
      super(-1, typeId, name, (char) 0, null, null);
      myDescription = description;
      myAllStatuses = toArray(allStatuses);
      myAllowsRequestee = allowsRequestee;
    }

    public FlagInfo(int flagId, int typeId, BugzillaUser setter, BugzillaUser requestee, FlagInfo info) {
      super(flagId, typeId, info.getName(), info.getStatus(), setter, requestee);
      myDescription = info.myDescription;
      myAllStatuses = info.myAllStatuses;
      myAllowsRequestee = info.myAllowsRequestee;
    }

    @Nullable
    public Boolean isAllowsRequestee() {
      return myAllowsRequestee;
    }

    private static char[] toArray(List<Character> list) {
      if (list.isEmpty()) return Const.EMPTY_CHARS;
      else {
        char[] array = new char[list.size()];
        for (int i = 0; i < list.size(); i++) {
          Character s = list.get(i);
          array[i] = s;
        }
        return array;
      }
    }

    public boolean isType() {
      if (getFlagId() >= 0) {
        assert getStatus() != 0; // flag has status
        return false;
      } else {
        assert getStatus() == 0; // types has no status set
        assert getTypeId() >= 0; // types has type id
        return true;
      }
    }

    public String getDescription() {
      return myDescription;
    }

    public char[] getAllStatuses() {
      return myAllStatuses;
    }

    public static int findByFlagId(List<FlagInfo> infos, int flagId) {
      for (int i = 0; i < infos.size(); i++) {
        FlagInfo info = infos.get(i);
        if (info.getFlagId() == flagId) return i;
      }
      return -1;
    }

    public BugInfo.Flag merge(BugInfo.Flag other) {
      assert other.getFlagId() < 0 || getFlagId() == other.getFlagId() : getFlagId() + " " + other.getFlagId();
      assert getTypeId() < 0 || getTypeId() == other.getTypeId() : getTypeId() + " " + other.getTypeId(); // type can be merged only with same type
      BugzillaUser otherRequestee = other.getRequestee();
      if(!Util.equals(getRequestee(), otherRequestee)) {
        // todo: we could guess the suffix, warn the user
        Log.warn("FlagInfo.merge: " + getRequestee() + " vs " + otherRequestee + ", emailsuffix problem?");
      }
      int flagId = getFlagId() >= 0 ? getFlagId() : other.getFlagId();
      int typeId = getTypeId() >= 0 ? getTypeId() : other.getTypeId();
      BugzillaUser setter = getSetter() != null ? getSetter().mergeWith(other.getSetter()) : other.getSetter();
      BugzillaUser requestee = otherRequestee != null ? otherRequestee.mergeWith(getRequestee()) : getRequestee();
      return flagId != getFlagId() || typeId != getTypeId() || setter != getSetter() ? new FlagInfo(flagId, typeId, setter,
        requestee, this) : this;
    }

    public static int findByFlag(List<FlagInfo> infos, BugInfo.Flag flag) {
      for (int i = 0; i < infos.size(); i++) {
        FlagInfo info = infos.get(i);
        if (info.isType()) continue;
        if (info.getStatus() != flag.getStatus()) continue;
        if (!Util.equals(info.getRequestee(), flag.getRequestee())) continue;
        if (!Util.equals(info.getName(), flag.getName())) continue;
        return i;
      }
      return -1;
    }
  }
}