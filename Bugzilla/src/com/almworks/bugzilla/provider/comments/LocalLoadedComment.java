package com.almworks.bugzilla.provider.comments;

import java.math.BigDecimal;

/**
 * @author : Dyoma
 */
public interface LocalLoadedComment {
  void setText(String text);

  boolean isChanged();

  int getLocalIndex();

  LoadedCommentKey getCommentKey();

  void setPrivacy(Boolean privacy);

  void setWorkTime(BigDecimal workTime);

  BigDecimal getWorkTime();
}
