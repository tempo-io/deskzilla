package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.util.collections.Convertor;
import org.jetbrains.annotations.*;

// todo #802 instances should be static
/**
 * @author : Dyoma
 */
public abstract class TextResolver extends Convertor<String, ItemKey> {
  public abstract boolean isSameArtifact(ItemKey key, String text);

  public abstract ItemKey getItemKey(@NotNull String text);

  public abstract long resolve(String text, UserChanges changes) throws BadItemKeyException;

  @Override
  public ItemKey convert(String value) {
    return value == null ? null : getItemKey(value);
  }

  public static class UnresolvedItem extends ItemKeyStub {
    private final TextResolver myResolver;

    public UnresolvedItem(@NotNull TextResolver resolver, @NotNull String id) {
      super(id);
      myResolver = resolver;
    }

    @Override
    public long resolveOrCreate(UserChanges changes) throws BadItemKeyException {
      return myResolver.resolve(getId(), changes);
    }
  }
}
