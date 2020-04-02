package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.oper.CommentsMatcher;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.util.Pair;
import com.almworks.util.collections.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;

/**
 * This is a transfer object that holds info about single bugzilla bug.
 *
 * @author sereda
 */
public class BugInfo extends BugInfoMinimal {
  public static final Convertor<BugInfo, Integer> TO_ID = new Convertor<BugInfo, Integer>() {
    public Integer convert(BugInfo bug) {
      return bug.getID();
    }
  };

  /**
   * If null, then not loaded
   */
  @Nullable
  private List<Attachment> myAttachments;

  private final List<Comment> myComments = Collections15.arrayList();
  private final BugzillaValues myValues = new BugzillaValues();
  private final TimeZone myDefaultTimezone;

  private Comment[] mySortedComments = null;
  private ErrorType myError = null;

  private MultiMap<String, String> myCustomFieldValues;

  // extra
  private List<Pair<BugGroupData, Boolean>> myGroups;
  private Map<String, CustomFieldInfo> myCustomFieldInfo;


  @Nullable
  private Boolean myUserSeesPrivateComments;

  @Nullable
  private List<Pair<BugzillaUser, Integer>> myVotes;

  // bugzilla 3.1+
  /**
   * Contains list of allowed status changes for the current status of this bug.
   * Each status is also detected as open or closed.
   * If null, not available
   */
  @Nullable
  private List<StatusInfo> myAllowedStatusChanges;

  /**
   * Contains target status for the action "mark as duplicate". Resolution is hard-coded to
   * be "DUPLICATE".
   */
  @Nullable
  private String myMarkDuplicateStatus;

  @Nullable
  private List<Flag> myBugFlags = null;
  /**
   * When true means that the object contains all flag type applicable to bug's component. And has all allowed statuses
   * for the flag types.
   */
  @Nullable
  private Boolean myHasAccurateFlagInfo = null;
  /**
   * "Really" means that we're sure there are no flags on server for this bug.
   * True iff this info has been updated with at least one front page data and none of them had flags.
   */
  private boolean myReallyHasNoFlags = false;


  public BugInfo(TimeZone defaultTimezone) {
    super(null, null);
    myDefaultTimezone = defaultTimezone;
  }

  public BugInfo copy() {
    BugInfo result = new BugInfo(myDefaultTimezone);
    result.myValues.copy(myValues);
    result.myError = myError;
    result.myComments.addAll(myComments);
    result.mySortedComments = mySortedComments;

    List<Attachment> attachments = myAttachments;
    result.myAttachments = attachments == null ? null : Collections15.arrayList(attachments);

    result.myCustomFieldValues = MultiMap.createCopyOrNull(myCustomFieldValues);
    result.myCustomFieldInfo = Collections15.linkedHashMapCopyOrNull(myCustomFieldInfo);

    result.myAllowedStatusChanges = myAllowedStatusChanges; // immutable
    result.myMarkDuplicateStatus = myMarkDuplicateStatus;

    List<Pair<BugzillaUser, Integer>> votes = myVotes;
    result.myVotes = votes == null ? null : Collections15.arrayList(votes);

    result.myGroups = myGroups == null ? null : Collections15.arrayList(myGroups);

    result.myUserSeesPrivateComments = myUserSeesPrivateComments;
    return result;
  }

  public TimeZone getDefaultTimezone() {
    return myDefaultTimezone;
  }

  @Nullable
  public List<StatusInfo> getAllowedStatusChanges() {
    return myAllowedStatusChanges;
  }

  @Nullable
  public String getMarkDuplicateStatus() {
    return myMarkDuplicateStatus;
  }

  @Nullable
  public List<Pair<BugzillaUser, Integer>> getVotes() {
    return myVotes;
  }

  public void updateWithVotes(List<Pair<BugzillaUser, Integer>> pairs) {
    myVotes = pairs;
  }

  @NotNull
  public Comment[] getOrderedComments() {
    int count = myComments.size();
    if (mySortedComments != null) {
      assert mySortedComments.length == count : mySortedComments + " " + myComments;
      return mySortedComments;
    }
    sortComments();
    mySortedComments = myComments.toArray(new Comment[count]);
    return mySortedComments;
  }

  private void sortComments() {
    int count = myComments.size();
    if (count < 3) {
      Collections.sort(myComments, Comment.TIME_ORDER);
    } else {
      int comp = Comment.TIME_ORDER.compare(myComments.get(1), myComments.get(count - 1));
      if (comp == 0) {
        Collections.sort(myComments, Comment.TIME_ORDER);
      } else {
        if (comp > 0) { // Reversed
          int firstComp = Comment.TIME_ORDER.compare(myComments.get(0), myComments.get(1));
          if (firstComp >= 0) // Full reverse
            Collections.reverse(myComments);
          else
            Collections.reverse(myComments.subList(1, count));
        }
        if (!CollectionUtil.isSorted(myComments, Comment.TIME_ORDER)) {
//          assert notifyWrongOrder(result);
          Collections.sort(myComments, Comment.TIME_ORDER);
        }
      }
    }
  }

//  private boolean notifyWrongOrder(Comment[] comments) {
//    Log.warn(TextUtil.separate(comments, "\n----------\n", Convertors.getToString()));
//    return true;
//  }

  public ErrorType getError() {
    return myError;
  }

  public void setError(ErrorType error) {
    myError = error;
  }

  public long getMTime() {
    String s = getStringMTime();
    if (s == null || s.length() == 0 || "0".equals(s))
      return 0;
    return BugzillaDateUtil.parseOrWarn(s, myDefaultTimezone).getTime();
  }

  public String getStringID() {
    return myValues.getMandatoryScalarValue(BugzillaAttribute.ID);
  }

  public String getStringMTime() {
    return myValues.getScalarValue(BugzillaAttribute.MODIFICATION_TIMESTAMP, "0");
  }

  public BugzillaValues getValues() {
    return myValues;
  }

  public String toString() {
    String id = myValues.getMandatoryScalarValue(BugzillaAttribute.ID);
    return id == null ? "bug<?>" : "bug<" + id + ">";
  }

  public boolean hasNoFlagsOnServer() {
    return myReallyHasNoFlags && (myBugFlags == null || myBugFlags.isEmpty()); 
  }

  public void updateWith(FrontPageData fpd) {
    myValues.clear(BugzillaAttribute.DELTA_TS);
    myValues.put(BugzillaAttribute.DELTA_TS, fpd.getDeltaTs());
    myGroups = Collections15.arrayList(fpd.getGroups());
    List<FrontPageData.CommentInfo> commentInfo = fpd.getCommentPrivacyInfo();
    myUserSeesPrivateComments = commentInfo != null;
    if (commentInfo != null) {
      updateCommentsWith(commentInfo);
    }

    updateCustomFieldValues(fpd.getCustomFieldValues());
    updateCustomFieldInfo(fpd.getCustomFieldInfo());

    List<StatusInfo> allowedStatusChanges = fpd.getAllowedStatusChanges();
    myAllowedStatusChanges =
      allowedStatusChanges == null ? null : Collections15.unmodifiableListCopy(allowedStatusChanges);
    myMarkDuplicateStatus = fpd.getMarkDuplicateStatus();

    Integer voteCount = fpd.getVoteCount();
    if (voteCount != null) {
      String currentVotes = myValues.getScalarValue(BugzillaAttribute.TOTAL_VOTES, null);
      if (currentVotes != null) {
        if (!currentVotes.equals(String.valueOf(voteCount))) {
          Log.warn(this + ": vote data differ: " + currentVotes + " " + voteCount);
        }
      } else {
        myValues.put(BugzillaAttribute.TOTAL_VOTES, String.valueOf(voteCount));
      }
    }
    mergeFlags(fpd);
  }

  private void mergeFlags(FrontPageData fpd) {
    if (myBugFlags == null) {
      myBugFlags = Collections15.<Flag>arrayList(fpd.getFlags());
      //noinspection ConstantConditions
      myReallyHasNoFlags = myBugFlags.isEmpty();
    } else {
      List<Flag> merged = Collections15.arrayList();
      List<FrontPageData.FlagInfo> infos = Collections15.arrayList();
      List<FrontPageData.FlagInfo> fpdFlags = fpd.getFlags();
      if (fpdFlags != null) {
        for (FrontPageData.FlagInfo info : fpdFlags) {
          if (info.isType()) merged.add(info);
          else infos.add(info);
        }
      }
      myReallyHasNoFlags &= infos.isEmpty();
      boolean fpdMissingFlags = infos.isEmpty();
      //noinspection ConstantConditions
      for (Flag flag : myBugFlags) {
        if (flag.isType()) continue;
        int id = flag.getFlagId();
        int index;
        if (id < 0) index = FrontPageData.FlagInfo.findByFlag(infos, flag);
        else index = FrontPageData.FlagInfo.findByFlagId(infos, id);
        if (index >= 0) {
          FrontPageData.FlagInfo info = infos.remove(index);
          merged.add(info.merge(flag));
        } else {
          fpdMissingFlags = true;
          merged.add(flag);
        }
      }
      if (!infos.isEmpty()) {
        fpdMissingFlags = true;
        Log.warn("Some flags not merged " + infos + " (merged: " + merged + ") bug#" + getStringID());  // different flags provided in XML and FPD or error
      }
      myBugFlags = merged;
      myHasAccurateFlagInfo = !fpdMissingFlags;
    }
  }

  private void updateCustomFieldInfo(Map<String, CustomFieldInfo> cfinfo) {
    if (cfinfo == null || cfinfo.size() == 0)
      return;
    if (myCustomFieldInfo == null)
      myCustomFieldInfo = Collections15.linkedHashMap();
    myCustomFieldInfo.putAll(cfinfo);
  }

  private void updateCustomFieldValues(MultiMap<String, String> cfvalues) {
    if (cfvalues == null || cfvalues.size() == 0)
      return;
    if (myCustomFieldValues == null)
      myCustomFieldValues = MultiMap.create();
    for (String key : cfvalues.keySet()) {
      myCustomFieldValues.removeAll(key);
    }
    myCustomFieldValues.addAll(cfvalues);
  }

  private void updateCommentsWith(List<FrontPageData.CommentInfo> commentInfo) {
    int count = myComments.size();
    if (count == 0)
      return;
    Comment c = myComments.get(0);
    if (c.isPrivacyKnown())
      return;
    sortComments();
    for (FrontPageData.CommentInfo ci : commentInfo) {
      if (ci.privacy == null) {
        // nothing to update
        continue;
      }
      if (ci.sequence < 0 || ci.text == null) {
        assert false : ci;
        continue;
      }
      int index = findComment(ci.sequence, ci.date, ci.text);
      if (index < 0 || index >= count) {
        Log.warn("cannot find comment [" + ci.text + "] when updating with fpd");
        continue;
      }
      Comment comment = myComments.get(index);
      Comment newComment = new Comment(comment, ci);
      myComments.set(index, newComment);
      mySortedComments = null;
    }
  }

  private int findComment(int sequence, long date, final String text) {
    assert CollectionUtil.isSorted(myComments, Comment.TIME_ORDER);
    CommentsMatcher matcher = new CommentsMatcher(date) {
      protected boolean compareToSample(int index) {
        return BugzillaUtil.similarComments(myComments.get(index).getText(), text);
      }
    };
    int mod = myComments.size();
    int off = Math.min(sequence + 1, mod);
    for (int i = mod - 1; i >= 0; i--) {
      int k = (i + off) % mod;
      Comment comment = myComments.get(k);
      long cdate = comment.getWhenDate().getTime();
      matcher.acceptComment(k, cdate);
    }
    int found = matcher.search();
    if (found < 0)
      return -1;
    assert found >= 0 && found < myComments.size() : found + " " + myComments;
    return found;
  }

  /**
   * @return writable copy
   */
  @Nullable
  public List<Pair<BugGroupData, Boolean>> getGroups() {
    return myGroups == null ? null : Collections15.arrayList(myGroups);
  }

  @Nullable
  private Attachment[] getAttachments() {
    List<Attachment> attachments = myAttachments;
    return attachments == null ? null : attachments.toArray(new Attachment[attachments.size()]);
  }

  @Nullable
  public String getLastAttachmentId() {
    List<Attachment> attachments = myAttachments;
    if (attachments == null || attachments.size() == 0) {
      return null;
    } else {
      return attachments.get(attachments.size() - 1).id;
    }
  }

  @Nullable
  public Attachment removeLastAttachment() {
    List<Attachment> attachments = myAttachments;
    if (attachments == null)
      return null;
    int size = attachments.size();
    if (size > 0)
      return attachments.remove(size - 1);
    else
      return null;
  }

  @Nullable
  public Attachment[] fetchAttachments() {
    Attachment[] attachments = getAttachments();
    if (attachments != null && attachments.length != 0)
      return attachments;
    List<Attachment> list = null;
    Set<String> alreadyFound = null;
    for (Comment comment : myComments) {
      Attachment attachData = findAttachmentInComment(comment);
      if (attachData == null)
        continue;
      if (list == null)
        list = Collections15.arrayList();
      if (alreadyFound == null)
        alreadyFound = Collections15.hashSet();
      boolean added = alreadyFound.add(attachData.id);
      if (added)
        list.add(attachData);
    }
    return list == null ? attachments : list.toArray(new Attachment[list.size()]);
  }

  private static Attachment findAttachmentInComment(Comment comment) {
    String text = comment.getText();
    int firstLineEnd = text.indexOf('\n');
    if (firstLineEnd == -1)
      return null;
    int secondLineEnd = text.indexOf('\n', firstLineEnd + 1);
    if (secondLineEnd == -1)
      return null;
    if (firstLineEnd < 10 || firstLineEnd > 60) {
      // loo short or too long for a translation of "created an attachment (id=NNNNNN)"
      return null;
    }
    String line = text.substring(0, firstLineEnd);
    if (line.indexOf('>') >= 0) {
      // it's a reply
      return null;
    }
    Matcher matcher = BugzillaHTMLConstants.CREATED_AN_ATTACHMENT_COMMENT.matcher(line);
    if (!matcher.matches()) {
      return null;
    }
    String id = matcher.group(1);
    String description = text.substring(firstLineEnd + 1, secondLineEnd);
    String date = comment.getWhenString();
    Attachment attachData = new Attachment(id, date, description);
    return attachData;
  }

  public void addComment(Comment comment) {
    myComments.add(comment);
    mySortedComments = null;
  }

  public void setComments(Collection<Comment> comments) {
    myComments.clear();
    myComments.addAll(comments);
    mySortedComments = null;
  }

  public List<Comment> copyComments() {
    return Collections15.arrayList(myComments);
  }

  public void addAttachment(Attachment attachment) {
    List<Attachment> attachmentList = myAttachments;
    if (attachmentList == null)
      myAttachments = attachmentList = Collections15.arrayList();
    attachmentList.add(attachment);
  }

  public void setAttachmentsLoaded() {
    if (myAttachments == null)
      myAttachments = Collections15.arrayList();
  }

  @NotNull
  public List<Attachment> copyAttachments() {
    List<Attachment> attachments = myAttachments;
    return attachments == null ? Collections15.<Attachment>emptyList() : Collections15.arrayList(attachments);
  }

  public boolean removeComment(Comment comment) {
    boolean b = myComments.remove(comment);
    if (b) {
      mySortedComments = null;
    }
    return b;
  }

  @Nullable
  public Boolean getUserSeesPrivateComments() {
    return myUserSeesPrivateComments;
  }

  public void addCustomFieldSingleValue(String fieldId, String fieldValue) {
    if (myCustomFieldValues == null)
      myCustomFieldValues = MultiMap.create();
    myCustomFieldValues.add(fieldId, fieldValue);
  }

  @Nullable
  public MultiMap<String, String> getCustomFieldValues() {
    return MultiMap.createCopyOrNull(myCustomFieldValues);
  }

  @Nullable
  public Map<String, CustomFieldInfo> getCustomFieldInfo() {
    return myCustomFieldInfo == null ? null : Collections.unmodifiableMap(myCustomFieldInfo);
  }

  public void addFlag(Flag flag) {
    List<Flag> flags = myBugFlags;
    if (flags == null) {
      flags = Collections15.arrayList();
      myBugFlags = flags;
    }
    flags.add(flag);
  }

  @Nullable
  public List<Flag> getBugFlags() {
    return myBugFlags;
  }

  public static class Attachment {
    public static final Attachment[] EMPTY_ARRAY = {};

    public final String date;
    public final String description;
    public final String id;
    public final Boolean obsolete;
    public final Boolean patch;
    public final Boolean isprivate;
    public final String filename;
    public final String mimetype;
    public final Long size;
    private final byte[] myData;
    private final Flag[] myFlags;

    public Attachment(String id, String date, String description) {
      this(id, date, description, null, null, null, null, null, null, null, Collections15.<Flag>emptyList());
    }

    public Attachment(String id, String date, String description, Boolean obsolete, Boolean patch, Boolean isprivate,
      String filename, String mimetype, Long size, byte[] data, List<Flag> flags)
    {
      this.id = id;
      this.date = date;
      this.description = description;
      this.obsolete = obsolete;
      this.patch = patch;
      this.isprivate = isprivate;
      this.filename = filename;
      this.mimetype = mimetype;
      this.size = size;
      myData = data;
      if (flags == null || flags.isEmpty()) myFlags = Flag.EMPTY_ARRAY;
      else myFlags = flags.toArray(new Flag[flags.size()]);
    }

    @NotNull
    public Flag[] getFlags() {
      return myFlags;
    }

    public byte[] getDataInternal() {
      return myData;
    }

    @Nullable
    public Integer getId() {
      try {
        return Integer.valueOf(id);
      } catch (NumberFormatException e) {
        Log.warn("cannot parse attachment id " + id);
        return null;
      }
    }
  }


  /**
   * Represent info about flag (bug or attachment), but not flagType, types are represented by subclasses only. <br>
   * Properties of object instanceof Flag:<br>
   * id and typeId may be not know at all, or only one of them is known (for flag) (old Bugzilla).<br>
   * if typeId is known, but id isnt - this is flag type. <br>
   * Bugzilla flag always has setter, but setter may be not known, in such case it is null. Types never has setter. <br>
   * Requestee is always known, or known that flag has no requestee, (types has no requestee). Null means this is type,
   * or the flag requestee is surely not set.<br>
   * status has value 0 for types and one of {X, ?, +, -} for flags. Always has actual server state.
   */
  public static class Flag {
    public static final Flag[] EMPTY_ARRAY = new Flag[0];
    private final int myId; // -1 if not available
    private final int myTypeId; // -1 if not available
    private final String myName;
    private final char myStatus; // X, ?, +, -
    private final BugzillaUser mySetter;
    @Nullable
    private final BugzillaUser myRequestee;

    public Flag(int id, int typeId, String name, char status, @Nullable BugzillaUser setter, @Nullable BugzillaUser requestee) {
      myId = id;
      myTypeId = typeId;
      myName = fixHyphen(name);
      myStatus = status;
      mySetter = setter;
      myRequestee = requestee;
    }

    private static String fixHyphen(String name) {
      if(name == null) {
        return null;
      }
      // BZ puts non-breaking hyphens into HTML instead of
      // regular hyphens in flag type names
      return name.replace('\u2011', '-');
    }

    public static char parseStatus(String strStatus) {
      if ("?".equals(strStatus)) return '?';
      else if ("+".equals(strStatus)) return '+';
      else if ("-".equals(strStatus)) return '-';
      else if ("X".equals(strStatus)) return 'X';
      else {
        Log.error("Unknown status " + strStatus);
        return 0;
      }
    }

    /**
     * Always -1 for types. For flags always know on new Bugzillas and may absent on olds.
     */
    public int getFlagId() {
      return myId;
    }

    /**
     * Always known for types. For flags always absent on old Bugzillas
     */
    public int getTypeId() {
      return myTypeId;
    }

    /**
     * Flag status, always in {X, ?, +, -} for flags and always 0 for types
     */
    public char getStatus() {
      return myStatus;
    }

    /**
     * Flag or type name, always know
     */
    public String getName() {
      return myName;
    }

    /**
     * Types: always null<br>
     * Flags: May be not known, but always not null on server.
     */
    public BugzillaUser getSetter() {
      return mySetter;
    }

    /**
     * Types: always null<br>
     * Flags: Always known. May absent on server
     */
    @Nullable
    public BugzillaUser getRequestee() {
      return myRequestee;
    }

    /**
     * @return {@link Boolean#TRUE} iff the flag has requestee or bug page allows to set requestee. <br>
     * {@link Boolean#FALSE} iff bug page has does not allow to set requestee.<br>
     * null in other cases. Means that it is not known if the flag is specifically requestable.
     */
    @Nullable
    public Boolean isAllowsRequestee() {
      return myRequestee != null ? Boolean.TRUE : null;
    }

    public boolean isType() {
      assert myStatus != 0;
      return false;
    }

    @Override
    public String toString() {
      String name = getClass().getName();
      int index = name.lastIndexOf('.');
      if (index >= 0) name = name.substring(index + 1);
      return name + " fId=" + myId + " tId=" +myTypeId + " name=" + myName + " status=" + myStatus + " setter=" + mySetter + " req=" + myRequestee;
    }

    @Nullable
    public static <F extends Flag> F findById(Collection<F> flags, int id) {
      if (flags == null || flags.isEmpty()) return null;
      if (id < 0) return null;
      for (F flag : flags) if (flag.getFlagId() == id) return flag;
      return null;
    }

    @Nullable
    public static <F extends Flag> F findTypeById(Collection<F> flags, int typeId) {
      if (flags == null || flags.isEmpty()) return null;
      if (typeId < 0) return null;
      for (F flag : flags) if (flag.getTypeId() == typeId && flag.isType()) return flag;
      return null;
    }

    @Nullable
    public static <F extends Flag> F findFirstFlagByName(Collection<F> flags, String flagName) {
      if (flags == null || flags.isEmpty()) return null;
      if (flagName == null || flagName.length() == 0) return null;
      for (F flag : flags) if (!flag.isType() && Util.equals(flag.getName(), flagName)) return flag;
      return null;
    }
  }

  public static class ErrorType {
    private final String myName;
    public static final ErrorType BUG_ACCESS_DENIED = new ErrorType("access is denied");
    public static final ErrorType BUG_NOT_FOUND = new ErrorType("bug is not found");

    private ErrorType(String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public String toString() {
      return getName();
    }
  }
}