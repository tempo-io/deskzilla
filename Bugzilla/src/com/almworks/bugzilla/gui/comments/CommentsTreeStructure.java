package com.almworks.bugzilla.gui.comments;

import com.almworks.api.application.viewer.CommentsController;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.util.advmodel.AListModel;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dyoma
 */
public class CommentsTreeStructure implements CommentsController.CommentsTree<LoadedCommentKey, LoadedCommentKey.CommentPlace, CommentNode> {
  private static final Pattern REPLY_PATTERN = Pattern.compile("^\\s*\\(in\\s+reply\\s+to\\s+comment\\s+#(\\d+)\\).*",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private Map<LoadedCommentKey.CommentPlace, String> myTransformedCommentTexts = Collections15.hashMap();

  public CommentNode createTreeNode(LoadedCommentKey element) {
    return new CommentNode(element);
  }

  public LoadedCommentKey.CommentPlace getNodeKey(LoadedCommentKey element) {
    return element.getCommentPlace();
  }

  @Nullable
  public LoadedCommentKey.CommentPlace getNodeParentKey(LoadedCommentKey element) {
    return detectParentIndex(element.getCommentPlace(), element.getText());
  }

  @Nullable
  private LoadedCommentKey.CommentPlace detectParentIndex(LoadedCommentKey.CommentPlace commentId, String commentText) {
    LoadedCommentKey.CommentPlace parent;
    parent = detectSimpleReference(commentText);
    if (parent != null)
      return parent;
    parent = detectQuotedReference(commentId, commentText);
    if (parent != null)
      return parent;
    return null;
  }

  @Nullable
  LoadedCommentKey.CommentPlace detectQuotedReference(LoadedCommentKey.CommentPlace commentId, String text) {
    String deform = getQuotation(text);
    if (deform == null || deform.length() == 0)
      return null;
    for (Map.Entry<LoadedCommentKey.CommentPlace, String> entry : myTransformedCommentTexts.entrySet()) {
      LoadedCommentKey.CommentPlace key = entry.getKey();
      if (!commentId.isAfter(key))
        continue;
      if (entry.getValue().indexOf(deform) >= 0)
        return key;
    }
    return null;
  }

  private final static int LINE_START = 0;
  private final static int NO_QUOTATION = 1;
  private final static int QUOTATION_STARTED = 2;

  private final static int MIN_QUOTATION_LENGTH = 4;

  @Nullable
  public static String getQuotation(String text) {
    int state = LINE_START;
    char[] chars = text.toCharArray();
    StringBuffer result = new StringBuffer();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      switch (state) {
      case LINE_START:
        if (Character.isWhitespace(c))
          continue;
        else if (c == '>')
          state = QUOTATION_STARTED;
        else
          state = NO_QUOTATION;
        break;
      case QUOTATION_STARTED:
        if (c == '\n') {
          if (result.length() >= MIN_QUOTATION_LENGTH)
            return result.toString();
          state = LINE_START;
          result.setLength(0);
        } else if (Character.isLetterOrDigit(c))
          result.append(Character.toUpperCase(c));
        break;
      case NO_QUOTATION:
        if (c == '\n')
          state = LINE_START;
        break;
      default:
        assert false : state;
      }
    }
    return result.length() >= MIN_QUOTATION_LENGTH ? result.toString() : null;
  }

  @Nullable
  public static LoadedCommentKey.CommentPlace detectSimpleReference(String commentText) {
    Matcher match = REPLY_PATTERN.matcher(commentText);
    return match.matches() ? LoadedCommentKey.CommentPlace.create(match.group(1)) : null;
  }

  public void attachCommentsList(Lifespan lifespan, final AListModel<LoadedCommentKey> comments) {
    myTransformedCommentTexts.clear();
    lifespan.add(comments.addListener(new AListModel.Adapter() {
      public void onInsert(int index, int length) {
        putTransformedCommentTexts(comments.subList(index, index + length));
      }

      public void onItemsUpdated(AListModel.UpdateEvent event) {
        putTransformedCommentTexts(comments.subList(event.getLowAffectedIndex(), event.getHighAffectedIndex() + 1));
      }
    }));
    lifespan.add(comments.addRemovedElementListener(new AListModel.RemovedElementsListener<LoadedCommentKey>() {
      public void onBeforeElementsRemoved(AListModel.RemoveNotice<LoadedCommentKey> loadedComments) {
        removeTransformedCommentTexts(loadedComments.getList());
      }
    }));
    lifespan.add(new Detach() {
      protected void doDetach() {
        myTransformedCommentTexts.clear();
      }
    });
    putTransformedCommentTexts(comments.toList());
  }

  private void putTransformedCommentTexts(List<LoadedCommentKey> comments) {
    Map<LoadedCommentKey.CommentPlace, String> map = Collections15.hashMap();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < comments.size(); i++) {
      LoadedCommentKey comment = comments.get(i);
      map.put(comment.getCommentPlace(), comment.getText());
    }
    putTransformedCommentTexts(map);
  }

  void putTransformedCommentTexts(Map<LoadedCommentKey.CommentPlace, String> map) {
    for (Map.Entry<LoadedCommentKey.CommentPlace, String> entry : map.entrySet()) {
      String text = entry.getValue();
      if (text != null)
        myTransformedCommentTexts.put(entry.getKey(), getTransformedCommentText(text));
    }
  }

  private void removeTransformedCommentTexts(List<LoadedCommentKey> comments) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < comments.size(); i++) {
      LoadedCommentKey comment = comments.get(i);
      myTransformedCommentTexts.remove(comment.getCommentPlace());
    }
  }

  /**
   * Prepares a string for reply searching. Transformed text contains only characters that user
   * had written, without quoted lines and without whitespaces; all characters are uppercase.
   */
  @Nullable
  public static String getTransformedCommentText(String text) {
    if (text == null)
      return null;
    StringBuffer result = new StringBuffer(text.length());
    char[] chars = text.toCharArray();
    boolean lineStart = true;
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (c == '\n' || c == '\r') {
        lineStart = true;
        continue;
      }

      if (Character.isWhitespace(c))
        continue;

      if (lineStart && c == '>') {
        // skip quoted
        for (i++; i < chars.length; i++) {
          if (chars[i] == '\n' || chars[i] == '\r') {
            lineStart = true;
            break;
          }
        }
        continue;
      }

      lineStart = false;

      if (!Character.isLetterOrDigit(c))
        continue;

      result.append(Character.toUpperCase(c));
    }
    return result.toString();
  }
}
