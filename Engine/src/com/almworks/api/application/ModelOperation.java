package com.almworks.api.application;

import com.almworks.util.Pair;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Collection;

/**
 * @author dyoma
 */
public interface ModelOperation<T> {
  TypedKey<? extends Collection<String>> ADD_STRING_VALUE = TypedKey.create("addStringValue");
  TypedKey<? extends Collection<String>> ADD_NOT_EMPTY_STRING_VALUE = TypedKey.create("addNotEmptyStringValue");
  TypedKey<? extends Collection<Pair<String, Boolean>>> ADD_STRING_BOOL_VALUE = TypedKey.create("addStringBoolValue");
  TypedKey<? extends Collection<Pair<String, Boolean>>> ADD_NOT_EMPTY_STRING_BOOL_VALUE = TypedKey.create("addNotEmptyStringBoolValue");
  TypedKey<String> SET_STRING_VALUE = TypedKey.create("setStringValue");
  TypedKey<ItemKey> SET_ITEM_KEY = TypedKey.create("setItemKey");

  void perform(ItemUiModel model, T argument) throws CantPerformExceptionExplained;

  /**
   * Checks value for any problem
   * @param argument value to be checked
   * @return <code>null</code> if value has no problem and can be committed.
   * <br> Not null result means that value can't be committed right now and contains problem description. Empty string
   * means that default problem description should be shown to user
   */
  @Nullable
  String getArgumentProblem(T argument);
}
