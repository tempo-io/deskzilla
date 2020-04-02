package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.engine.util.CommentsLinkHelper;
import com.almworks.util.tests.BaseTestCase;

/**
 * :todoc:
 *
 * @author sereda
 */
public class CommentsLinkTests extends BaseTestCase {
  public static final int MAX_VIEW_SCAN_COUNT = 20;

  public void testHashPayload() {
    assertEquals(
      CommentsLinkHelper.getCommentHash(""),
      CommentsLinkHelper.getCommentHash("     ")
    );
    assertEquals(
      CommentsLinkHelper.getCommentHash("Hello BIG GUYZ"),
      CommentsLinkHelper.getCommentHash("Hello! \n\n\t B I G   G U Y Z ")
    );

    assertNotSame(
      CommentsLinkHelper.getCommentHash("haba"),
      CommentsLinkHelper.getCommentHash("HABA")
    );
    assertNotSame(
      CommentsLinkHelper.getCommentHash("a\nb\nc\n"),
      CommentsLinkHelper.getCommentHash("abc\nd")
    );
  }

  public void testSubhashDistribution() {
    final int COUNT = CommentsLinkHelper.SUBHASH_MAGNITUDE * CommentsLinkHelper.SUBHASH_MAGNITUDE * CommentsLinkHelper.SUBHASH_MAGNITUDE;
    final int MEAN = MAX_VIEW_SCAN_COUNT;

    int[] buckets = new int[COUNT];
    int comments = COUNT * MEAN;

    for (int i = 0; i < comments; i++) {
      String commentText = "This is " + i + " " + i + " a not so long comment # " + i + " (" + Integer.toHexString(i) + ")";
      int hash = CommentsLinkHelper.getCommentHash(commentText).intValue();
      int index = sub(hash, 1) * 256 + sub(hash, 2) * 16 + sub(hash, 3);
      buckets[index]++;
    }

    final float ALLOWED_BUCKET_TO_MEAN_RATIO = 3.0F;
    final float ALLOWED_MEAN_DEVIATION_RATIO = 0.2F;

    float deviation = 0F;
    for (int i = 0; i < buckets.length; i++) {
      int bucket = buckets[i];
      float ratio = 1F * bucket / MEAN;
      assertTrue(i + ": " + ratio, ratio <= ALLOWED_BUCKET_TO_MEAN_RATIO);
      deviation += Math.abs(bucket - MEAN);
    }
    deviation = deviation / COUNT;
    float ratio = deviation / MEAN;
    assertTrue("d " + ratio, ratio < ALLOWED_MEAN_DEVIATION_RATIO);
  }

  private static int sub(int hash, int n) {
    int h = -1;
    if (n == 1)
      h = CommentsLinkHelper.subHash1(hash);
    else if (n == 2)
      h = CommentsLinkHelper.subHash2(hash);
    else if (n == 3)
      h = CommentsLinkHelper.subHash3(hash);
    else
      fail();
    assertTrue(h >= 0);
    assertTrue(h < CommentsLinkHelper.SUBHASH_MAGNITUDE);
    return h;
  }
}
