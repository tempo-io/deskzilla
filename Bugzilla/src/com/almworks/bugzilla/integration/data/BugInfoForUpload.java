package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.items.sync.ItemUploader;
import com.almworks.util.Pair;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class BugInfoForUpload {
  /**
   * Values that are supposed to be current before the update.
   */
  private final BugzillaValues myPrevValues = new BugzillaValues();
  private final BugzillaValues myNewValues = new BugzillaValues();

  private final List<CommentForUpload> myComments = Collections15.arrayList();

  /**
   * Changes to comment's privacy
   */
  @Nullable
  private List<CommentChangeData> myCommentPrivacyChanges;

  /**
   * Contains hints from application about what actions (in bugzilla terms) did user take
   * to achieve results contained in myNewValues.
   */
  //private final Map<BugzillaAction, Map<TypedKey<?>, ?>> myWorkflowActionHints = Collections15.hashMap();
  private final Set<String> myAddCC = Collections15.hashSet();
  private final Set<String> myRemoveCC = Collections15.hashSet();
  private final List<AttachData> myAttachments = Collections15.arrayList();
  // group id => turned on / off
  private final Map<BugGroupData, Boolean> myGroupsUpdate = Collections15.hashMap();

  @Nullable
  private Map<String, List<String>> myCustomFieldChanges;

  @Nullable
  private Integer myVoteValue;

  /**
   * Map flagId->newValue.
   */
  @Nullable
  private Map<Integer, FlagState> myFlagChanges;

  /**
   * Pairs {typeId,newValue}.
   */
  @Nullable
  private List<Pair<Integer, FlagState>> myAddFlags;
  private final AtomicReference<ItemUploader.UploadProcess> myUploadProcess = new AtomicReference<ItemUploader.UploadProcess>();

  public BugInfoForUpload() {
  }

  public BugInfoForUpload(BugInfoForUpload copyFrom) {
    myNewValues.copy(copyFrom.myNewValues);
    myPrevValues.copy(copyFrom.myPrevValues);
    myComments.addAll(copyFrom.myComments);
    myAddCC.addAll(copyFrom.myAddCC);
    myRemoveCC.addAll(copyFrom.myRemoveCC);
    myAttachments.addAll(copyFrom.myAttachments);
    myGroupsUpdate.putAll(copyFrom.myGroupsUpdate);
    myCommentPrivacyChanges =
      copyFrom.myCommentPrivacyChanges == null ? null : Collections15.arrayList(copyFrom.myCommentPrivacyChanges);
    myCustomFieldChanges = Collections15.linkedHashMapCopyOrNull(copyFrom.myCustomFieldChanges);
    myVoteValue = copyFrom.myVoteValue;
    myAddFlags = copyFrom.myAddFlags == null ? null : Collections15.arrayList(copyFrom.myAddFlags);
    myFlagChanges = copyFrom.myFlagChanges == null ? null : Collections15.hashMap(copyFrom.myFlagChanges);
    myUploadProcess.set(copyFrom.myUploadProcess.get());
  }

  @Nullable
  public Integer getVoteValue() {
    return myVoteValue;
  }

  public void setVoteValue(Integer voteValue) {
    myVoteValue = voteValue;
  }

  public void addCC(Set<String> CCs) {
    myAddCC.addAll(CCs);
  }

  public void addComment(String comment, @Nullable Boolean privacy, @Nullable BigDecimal timeWorked) {
    myComments.add(new CommentForUpload(comment, privacy, timeWorked));
  }

  @Nullable
  public List<CommentChangeData> getCommentPrivacyChanges() {
    return myCommentPrivacyChanges == null ? null : Collections.unmodifiableList(myCommentPrivacyChanges);
  }

  public void clearCommentPrivacyChanges() {
    myCommentPrivacyChanges = null;
  }

  public synchronized void addCommentPrivacyChange(long date, String comment, boolean privacy) {
    List<CommentChangeData> list = myCommentPrivacyChanges;
    if (list == null)
      myCommentPrivacyChanges = list = Collections15.arrayList();
    list.add(new CommentChangeData(date, BugzillaUtil.toleratingCommentHash(comment), privacy));
  }

  public Set<String> getAddCC() {
    return Collections.unmodifiableSet(myAddCC);
  }

  public List<CommentForUpload> getComments() {
    return Collections.unmodifiableList(myComments);
  }

  public BugzillaValues getPrevValues() {
    return myPrevValues;
  }

  public Set<String> getRemoveCC() {
    return Collections.unmodifiableSet(myRemoveCC);
  }

  public BugzillaValues getNewValues() {
    return myNewValues;
  }

  public void removeCC(Set<String> CCs) {
    myRemoveCC.addAll(CCs);
  }

  /**
   * Returns new value, if it exists (and not was unset). If it does not exist, or was specifically
   * unset, returns previous value or default.
   */
  public String getAnyValue(BugzillaAttribute attribute, String defaultValue) {
    String value = getNewValues().getScalarValue(attribute, null);
    if (value == null)
      value = getPrevValues().getScalarValue(attribute, defaultValue);
    return value;
  }

  public void removeFirstComment() {
    if (myComments.size() > 0)
      myComments.remove(0);
  }

  public boolean hasAnythingToUpdate() {
    return myNewValues.getSize() > 0 || myAddCC.size() > 0 || myRemoveCC.size() > 0 || myComments.size() > 0 ||
      myAttachments.size() > 0 || myGroupsUpdate.size() > 0 ||
      (myCommentPrivacyChanges != null && myCommentPrivacyChanges.size() > 0) ||
      (myCustomFieldChanges != null && !myCustomFieldChanges.isEmpty()) ||
      (myAddFlags != null && !myAddFlags.isEmpty()) ||
      (myFlagChanges != null && !myFlagChanges.isEmpty())
    ;
  }

  public void clearAddedCC() {
    myAddCC.clear();
  }

  public void addAttachment(File file, String description, String mimeType, SuccessfulUploadHook hook) {
    myAttachments.add(new AttachData(file, description, mimeType, hook));
  }

  public boolean hasAttachments() {
    return myAttachments.size() > 0;
  }

  public AttachData[] getAttachments() {
    return myAttachments.toArray(new AttachData[myAttachments.size()]);
  }

  public void changeGroup(BugGroupData group, boolean belongsTo) {
    myGroupsUpdate.put(group, belongsTo);
  }

  /**
   * @return writable copy
   */
  public Map<BugGroupData, Boolean> getGroupChanges() {
    return Collections15.hashMap(myGroupsUpdate);
  }

  public void clearGroups() {
    myGroupsUpdate.clear();
  }

  public void changeCustomField(String fieldId, List<String> value) {
    Map<String, List<String>> map = myCustomFieldChanges;
    if (map == null)
      myCustomFieldChanges = map = Collections15.linkedHashMap();
    map.put(fieldId, value);
  }

  @NotNull
  public Map<String, List<String>> getCustomFieldChanges() {
    return myCustomFieldChanges == null
      ? Collections.<String, List<String>>emptyMap()
      : Collections.unmodifiableMap(myCustomFieldChanges);
  }

  public void clearCustomFieldChange(String fieldId) {
    if (myCustomFieldChanges == null) {
      assert false : fieldId;
      return;
    }
    myCustomFieldChanges.remove(fieldId);
  }

  public void changeFlag(String flagName, int id, char status, @Nullable String requestee) {
    if (requestee != null && status != '?') Log.error("Cannot set requestee for status " + status);
    if (myFlagChanges == null) myFlagChanges = Collections15.hashMap();
    assert myFlagChanges != null;
    //noinspection ConstantConditions
    myFlagChanges.put(id, new FlagState(flagName, status, requestee));
  }

  public Iterator<Map.Entry<Integer, FlagState>> getChangedFlags() {
    if (myFlagChanges == null) return Collections15.emptyIterator();
    return Collections.unmodifiableMap(myFlagChanges).entrySet().iterator();
  }

  public void createFlag(String flagName, int typeId, char status, String requestee) {
    if (requestee != null && status != '?') Log.error("Cannot set requestee for status " + status);
    if (myAddFlags == null) myAddFlags = Collections15.arrayList();
    //noinspection ConstantConditions
    myAddFlags.add(Pair.create(typeId, new FlagState(flagName, status, requestee)));
  }

  public Iterator<Pair<Integer, FlagState>> getAddedFlags() {
    if (myAddFlags == null) return Collections15.emptyIterator();
    return Collections.unmodifiableList(myAddFlags).iterator();
  }

  public boolean hasFlagsToAdd() {
    return myAddFlags != null && !myAddFlags.isEmpty();
  }

  @SuppressWarnings({"ConstantConditions"})
  public void removeCreateFlags(Collection<Pair<Integer, BugInfoForUpload.FlagState>> added) {
    if (myAddFlags != null) {
      int oldSize = myAddFlags.size();
      myAddFlags.removeAll(added);
      if (myAddFlags.size() + added.size() != oldSize)
        Log.error("Wrong flags removed " + myAddFlags + ", but requested " + added);
      if (myAddFlags.isEmpty()) myAddFlags = null;
    }
  }

  public ItemUploader.UploadProcess getUploadProcess() {
    return myUploadProcess.get();
  }

  public void uploadAllowed(ItemUploader.UploadProcess process) {
    if (!myUploadProcess.compareAndSet(null, process)) Log.error("Uploaded twice");
  }

  public static class AttachData {
    private final File myFile;
    private final String myDescription;
    private final String myMimeType;
    private final SuccessfulUploadHook myHook;

    public AttachData(File file, String description, String mimeType, SuccessfulUploadHook hook) {
      assert file != null;
      assert description != null;
      assert mimeType != null;
      myFile = file;
      myDescription = description;
      myMimeType = mimeType;
      myHook = hook;
    }

    public String getDescription() {
      return myDescription;
    }

    public File getFile() {
      return myFile;
    }

    public SuccessfulUploadHook getHook() {
      return myHook;
    }

    public String getMimeType() {
      return myMimeType;
    }
  }


  public static class CommentChangeData {
    public final long date;
    public final int textHash;
    public final boolean privacy;

    public CommentChangeData(long date, int textHash, boolean privacy) {
      this.date = date;
      this.textHash = textHash;
      this.privacy = privacy;
    }

    public String toString() {
      return new Date(date) + ":" + textHash + ":" + privacy;
    }
  }


  public static class CommentForUpload {
    private final String myComment;

    /**
     * Whether this comment is private or not.
     */
    @Nullable
    private final Boolean myPrivacy;

    /**
     * Whether to add time.
     */
    @Nullable
    private final BigDecimal myTimeWorked;

    public CommentForUpload(String comment, Boolean privacy, BigDecimal timeWorked) {
      myComment = comment;
      myPrivacy = privacy;
      myTimeWorked = timeWorked;
    }

    public Boolean getPrivacy() {
      return myPrivacy;
    }

    public BigDecimal getTimeWorked() {
      return myTimeWorked;
    }

    public String getComment() {
      return myComment;
    }
  }

  public static class FlagState {
    public final char myStatus;
    public final String myRequestee;
    public final String myFlagName;

    public FlagState(String flagName, char status, String requestee) {
      if (flagName == null) flagName = "<Unknown>";
      myFlagName = flagName;
      myStatus = status;
      myRequestee = requestee;
    }
  }
}
