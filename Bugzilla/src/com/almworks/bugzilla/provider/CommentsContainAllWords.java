package com.almworks.bugzilla.provider;

import com.almworks.api.constraint.Constraint;
import org.almworks.util.TypedKey;

import java.util.List;

public class CommentsContainAllWords implements Constraint {
  private static final TypedKey<CommentsContainAllWords> COMMENTS_CONTAIN_ALL_WORDS = TypedKey.create(CommentsContainAllWords.class);
  private final List<String> myWords;

  public CommentsContainAllWords(List<String> words) {
    myWords = words;
  }

  public List<String> getWords() {
    return myWords;
  }

  public TypedKey<? extends Constraint> getType() {
    return COMMENTS_CONTAIN_ALL_WORDS;
  }
}
