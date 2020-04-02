package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.gui.ResolverItemFactory;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.*;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.*;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedInt;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author : Dyoma
 */
public class CommentListModelKey extends MasterSlaveModelKey<LoadedCommentKey> {
  private final static TypedKey<SynchronizedInt> LOCAL_COMMENT_COUNTER = TypedKey.create("localCommentCounter");

  private final ResolverItemFactory myFactory;
  private final DescriptionModelKey myDescriptionKey;

  public CommentListModelKey(ResolverItemFactory factory) {
    super(CommentsLink.attrMaster, "comments", BugzillaUtil.getDisplayableFieldName("Comments"));
    myFactory = factory;
    myDescriptionKey = new DescriptionModelKey(factory);
  }

  public void addChanges(UserChanges changes) {
    addChangesEditComments(changes);
  }

  private void addChangesEditComments(UserChanges changes) {
    final Collection<LoadedCommentKey> newValue = changes.getNewValue(this);
    if(newValue.isEmpty()) {
      myDescriptionKey.addChanges(changes);
      Log.debug("Adding description");
      return;
    }

    try {
      for(final LoadedCommentKey comment : newValue) {
        comment.resolveOrCreate(changes);
      }
    } catch(BadItemKeyException e) {
      changes.invalidValue(this, getDisplayableName());
    }
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    values.put(LOCAL_COMMENT_COUNTER, new SynchronizedInt(1));
    super.extractValue(itemVersion, itemServices, values);
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    SynchronizedInt value = from.get(LOCAL_COMMENT_COUNTER);
    assert value != null : this;
    to.put(LOCAL_COMMENT_COUNTER, new SynchronizedInt(value.get()));
    super.copyValue(to, from);
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return super.isEqualValue(models, values)
      && myDescriptionKey.isEqualValue(models, values);
  }

  @Override
  protected List<LoadedCommentKey> extractValues(LongList items, ItemVersion primaryItem, LoadedItemServices itemServices, PropertyMap values) {
    final SynchronizedInt counter = values.get(LOCAL_COMMENT_COUNTER);
    assert counter != null : this;
    final List<LoadedCommentKey> result = Collections15.arrayList();
    final ItemKeyCache resolver = itemServices.getItemKeyCache();
    final DBReader reader = primaryItem.getReader();

    Long connection = primaryItem.getValue(SyncAttributes.CONNECTION);
    List<String> texts = CommentsLink.attrText.collectValues(items, reader);
    List<BigDecimal> workTimes = CommentsLink.attrWorkTime.collectValues(items, reader);
    List<Boolean> privacies = CommentsLink.attrPrivate.collectValues(items, reader);
    List<Date> whens = CommentsLink.attrDate.collectValues(items, reader);
    List<Long> whos = CommentsLink.attrAuthor.collectValues(items, reader);
    int maxIndex = 0;
    List<ItemVersion> comments = primaryItem.readItems(items);
    LongArray localComments = new LongArray();
    for (int i = 0, readItemsSize = comments.size(); i < readItemsSize; i++) {
      ItemVersion comment = comments.get(i);
      final String text = Util.NN(texts.get(i), "");
      final Boolean privacy = privacies.get(i);
      final BigDecimal workTime = workTimes.get(i);
      if (!CommentsLink.isFinal(comment)) {
        localComments.add(comment.getItem());
        continue;
      }
      if (connection == null) connection = comment.getValue(SyncAttributes.CONNECTION);
      if (connection == null) {
        Log.error("Missing connection " + comment);
        continue;
      }

      final Date when = whens.get(i);
      assert when != null;
      final Long who = whos.get(i);
      assert who != null && who > 0;
      final ItemKey whoKey = resolver.getItemKeyOrNull(who, reader, User.KEY_FACTORY);
      Integer index = comment.getValue(CommentsLink.attrIndex);
      if (index == null) index = maxIndex + 1;
      else maxIndex = Math.max(maxIndex, index);
      result.add(new FinalCommentKey(whoKey, when, text, index, comment.getItem(), privacy, workTime, connection));
    }
    for (ItemVersion comment : primaryItem.readItems(localComments)) {
      String text = Util.NN(comment.getValue(CommentsLink.attrText));
      Boolean privacy = comment.getValue(CommentsLink.attrPrivate);
      BigDecimal workTime = comment.getValue(CommentsLink.attrWorkTime);
      if (connection == null) connection = comment.getValue(SyncAttributes.CONNECTION);
      if (connection == null) {
        Log.error("Missing connection " + comment);
        continue;
      }
      maxIndex++;
      result.add(new DBLocalCommentKey(comment.getItem(), text, maxIndex, counter.increment(), privacy, workTime, connection));
    }
    return result;
  }

  public String[] getCommentsText(ItemVersion bug) {
    final LongList comments = bug.getSlaves(CommentsLink.attrMaster);
    final String[] texts = new String[comments.size()];
    for(int i = 0; i < comments.size(); i++) {
      texts[i] = bug.forItem(comments.get(i)).getValue(CommentsLink.attrText);
    }
    return texts;
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    to.put(LOCAL_COMMENT_COUNTER, new SynchronizedInt(from.get(LOCAL_COMMENT_COUNTER).get()));
    super.takeSnapshot(to, from);
    myDescriptionKey.takeSnapshot(to, from);
  }

  public LocalLoadedComment createNewComment(String text, @Nullable Boolean privacy, @Nullable BigDecimal workTime, ModelMap map) {
    NewCommentKey newComment = createNewCommentImpl(text, privacy, map, workTime);
    Change change = change(map);
    change.newValue().add(newComment);
    change.done();
    return newComment;
  }

  @SuppressWarnings({"unchecked", "RedundantCast"})
  @Override
  public <T> ModelOperation<T> getOperation(@NotNull TypedKey<T> key) {
    if (ModelOperation.ADD_STRING_BOOL_VALUE.equals(key)) {
      return (ModelOperation) new AddCommentWithPrivacyOperation(true);
    } else if (ModelOperation.ADD_NOT_EMPTY_STRING_BOOL_VALUE.equals(key)) {
      return (ModelOperation<T>) new AddCommentWithPrivacyOperation(false);
    } else if (ModelOperation.ADD_STRING_VALUE.equals(key)) {
      return (ModelOperation) new AddCommentOperation(true);
    } else if (ModelOperation.ADD_NOT_EMPTY_STRING_VALUE.equals(key)) {
      return (ModelOperation) new AddCommentOperation(false);
    }
    return super.getOperation(key);
  }

  private NewCommentKey createNewCommentImpl(String text, @Nullable Boolean privacy, ModelMap map, BigDecimal workTime) {
    NewCommentKey newComment = new NewCommentKey(myFactory, map.get(LOCAL_COMMENT_COUNTER).increment());
    newComment.setText(text);
    newComment.setPrivacy(privacy);
    newComment.setWorkTime(workTime);
    return newComment;
  }

  public DescriptionModelKey getDescriptionKey() {
    return myDescriptionKey;
  }

  protected CanvasRenderer<PropertyMap> createRenderer() {
    return new CanvasRenderer<PropertyMap>() {
      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        Collection<LoadedCommentKey> comments = getValue(item);
        int commentsCount = Math.max(comments.size() - 1, 0);
        if (commentsCount == 0) {
          canvas.appendText(L.content("No comments"));
        } else {
          String noun = English.getSingularOrPlural("comment", commentsCount);
          canvas.appendText(L.content(commentsCount + " " + noun));
        }
      }
    };
  }

  public ModelMergePolicy getMergePolicy() {
    return new ModelMergePolicy.AbstractPolicy() {
      public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
        mergeIntoModel(key, model, base, branch);
        return true;
      }

      public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
        Collection<LoadedCommentKey> newList = getValue(newLocal);
        Change change = change(model);
        List<LoadedCommentKey> newValue = change.newValue();
        ServerComment.SERVER_COMMENT.removeAllFrom(newValue);
        newValue.addAll(0, ServerComment.SERVER_COMMENT.select(newList));
        change.done();
      }
    };
  }

  public boolean isExportable(Collection<Connection> connections) {
    return false;
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.ALWAYS;
  }

  private class AddCommentOperation implements ModelOperation<Collection<String>> {
    private final boolean myAllowEmpty;

    public AddCommentOperation(boolean allowEmpty) {
      myAllowEmpty = allowEmpty;
    }

    public void perform(ItemUiModel model, Collection<String> argument) {
      for (String comment : argument) {
        if (comment.trim().length() == 0)
          return;
        createNewComment(comment, false, null, model.getModelMap());
      }
    }

    public String getArgumentProblem(Collection<String> argument) {
      if (myAllowEmpty)
        return null;
      for (String comment : argument) {
        if (comment.trim().length() == 0)
          return "Please write a comment";
      }
      return null;
    }
  }

  private class AddCommentWithPrivacyOperation implements ModelOperation<Collection<Pair<String, Boolean>>> {
    private final boolean myAllowEmpty;

    public AddCommentWithPrivacyOperation(boolean allowEmpty) {
      myAllowEmpty = allowEmpty;
    }

    public void perform(ItemUiModel model, Collection<Pair<String, Boolean>> argument) {
      for (Pair<String, Boolean> comment : argument) {
        String text = comment.getFirst();
        if (text.trim().length() == 0)
          return;
        createNewComment(text, comment.getSecond(), null, model.getModelMap());
      }
    }

    public String getArgumentProblem(Collection<Pair<String, Boolean>> argument) {
      if (myAllowEmpty)
        return null;
      for (Pair<String, Boolean> comment : argument) {
        if (comment.getFirst().trim().length() == 0)
          return "Please write a comment";
      }
      return null;
    }
  }
}
