package com.almworks.api.application;

import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.ThreadAWT;
import org.jetbrains.annotations.*;

/**
 * Supports verification of item values for a model key.
 */
public interface ModelKeyVerifier {
  /**
   * Checks whether value for this model key is valid in the specified item.
   * @param item
   * @return null if item's model key value is valid in current context, exception with displayable error message otherwise
   */
  @ThreadAWT
  @Nullable(documentation = "when valid")
  String verify(@NotNull PropertyMap item);

  /**
   * Checks whether an edit did not introduce new errors. If error state didn't change as a result of the edit, does not report it.
   * @param oldState item state before the edit. If null, behaves as {@link #verify} for newState
   * @param newState item state after the edit
   * @return null if the edit didn't introduce new errors, new error displayable description otherwise
   */
  @Nullable
  String verifyEdit(@Nullable PropertyMap oldState, @NotNull PropertyMap newState);
}
