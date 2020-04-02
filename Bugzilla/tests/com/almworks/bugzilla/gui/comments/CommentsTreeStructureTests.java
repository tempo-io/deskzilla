package com.almworks.bugzilla.gui.comments;

import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.util.Map;

/**
 * @author Vasya
 */
public class CommentsTreeStructureTests extends BaseTestCase {
  private final String commentText1 = "text1";
  private final String commentText2 = "(In reply to comment #1)\n> text1\ntext2";
  private final String commentText3 = "(In reply to comment #2)\n> (In reply to comment #1)\ntext3";
  private final String commentText4 = "(In reply to comment #3)\n> (In reply to comment #2)\n" +
    "> > (In reply to comment #1)\n> text3\n\ntext4";
  private final String commentText5 = "(In reply to comment #4)\n" +
    "> (In reply to comment #3)\n" +
    "> > (In reply to comment #2)\n" +
    "> > > (In reply to comment #1)\n" +
    "> > text3\n" +
    "> \n" +
    "> text4\n" +
    "\n" +
    "text5";
  private final String commentText6 = "> text3\ntext6";

  public void testDetectSimpleReference() {
    assertNull(CommentsTreeStructure.detectSimpleReference(commentText1));
    assertEquals(LoadedCommentKey.CommentPlace.create(1), CommentsTreeStructure.detectSimpleReference(commentText2));
    assertEquals(LoadedCommentKey.CommentPlace.create(2), CommentsTreeStructure.detectSimpleReference(commentText3));
    assertEquals(LoadedCommentKey.CommentPlace.create(3), CommentsTreeStructure.detectSimpleReference(commentText4));
    assertEquals(LoadedCommentKey.CommentPlace.create(4), CommentsTreeStructure.detectSimpleReference(commentText5));
    assertNull(CommentsTreeStructure.detectSimpleReference(commentText6));
  }

//todo
  public void testDetectQuotedReference() {
    String[] s = new String[]{commentText1, commentText2, commentText3, commentText4, commentText5, commentText6};
    CommentsTreeStructure treeStructure = new CommentsTreeStructure();
    Map<LoadedCommentKey.CommentPlace, String> map = Collections15.hashMap();
    for (int i = 0; i < s.length; i++)
      map.put(LoadedCommentKey.CommentPlace.create(i * ((i % 2 == 0) ? 1 : -1)), s[i]);
    treeStructure.putTransformedCommentTexts(map);
    LoadedCommentKey.CommentPlace startFrom = LoadedCommentKey.CommentPlace.create(-4);
    assertNull(treeStructure.detectQuotedReference(startFrom, commentText1));
    assertEquals(LoadedCommentKey.CommentPlace.create(0), treeStructure.detectQuotedReference(startFrom, commentText2));
    assertEquals(LoadedCommentKey.CommentPlace.create(-1), treeStructure.detectQuotedReference(startFrom, commentText3));
    assertEquals(LoadedCommentKey.CommentPlace.create(2), treeStructure.detectQuotedReference(startFrom, commentText4));
    assertEquals(LoadedCommentKey.CommentPlace.create(-3), treeStructure.detectQuotedReference(startFrom, commentText5));
    assertEquals(LoadedCommentKey.CommentPlace.create(2), treeStructure.detectQuotedReference(startFrom, commentText6));
  }

  public void testTextTransformation() {
    assertNull(CommentsTreeStructure.getTransformedCommentText(null));
    assertEquals("TEXT1", CommentsTreeStructure.getTransformedCommentText(commentText1));
    assertEquals("INREPLYTOCOMMENT1TEXT2", CommentsTreeStructure.getTransformedCommentText(commentText2));
    assertEquals("INREPLYTOCOMMENT2TEXT3", CommentsTreeStructure.getTransformedCommentText(commentText3));
    assertEquals("INREPLYTOCOMMENT3TEXT4", CommentsTreeStructure.getTransformedCommentText(commentText4));
    assertEquals("INREPLYTOCOMMENT4TEXT5", CommentsTreeStructure.getTransformedCommentText(commentText5));
    assertEquals("TEXT6", CommentsTreeStructure.getTransformedCommentText(commentText6));
  }
}
