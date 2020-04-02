package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.util.English;
import com.almworks.util.commons.Function;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Set;

import static org.almworks.util.Collections15.linkedHashSet;

/**
 * Provides interface for bulk verification of model keys that depend on the same field.
 * This allows for less verbose and more informative error messages shown to user.
 */
public class CompositeDependentMkVerifier implements ModelKeyVerifier {
  /**
   * Values verified by my verifiers depend on this key.
   */
  private final ModelKey<ItemKey> myMainKey;
  private final Set<ModelKeyVerifier> myVerifiers = linkedHashSet();
  private static final String indent = "  ";

  public CompositeDependentMkVerifier(ModelKey<? extends ItemKey> mainKey) {
    myMainKey = (ModelKey<ItemKey>) mainKey;
  }

  @Override
  public String verify(@NotNull final PropertyMap item) {
    return collectErrors(null, item, new Function<ModelKeyVerifier, String>() {
      public String invoke(ModelKeyVerifier ver) {
        return ver.verify(item);
      }
    });
  }

  @Override
  public String verifyEdit(@Nullable final PropertyMap oldState, @NotNull final PropertyMap newState) {
    return collectErrors(oldState, newState, new Function<ModelKeyVerifier, String>() {
      public String invoke(ModelKeyVerifier ver) {
        return ver.verifyEdit(oldState, newState);
      }
    });
  }

  @Nullable
  private String collectErrors(@Nullable PropertyMap oldState, @NotNull PropertyMap newState, @NotNull Function<ModelKeyVerifier, String> erf) {
    StringBuilder ret = null;
    if (myVerifiers.isEmpty()) return null;

    String sep = "";
    String trail = "";
    String childrenTrail = indent;
    String errorPreamble = null;
    String oneErrorPreamble = getErrorPreamble(true, oldState, newState);
    String manyErrorsPreamble = getErrorPreamble(false, oldState, newState);
    for (ModelKeyVerifier ver : myVerifiers) {
      String msg = erf.invoke(ver);
      if (msg != null) {
        errorPreamble = (errorPreamble == null) ? oneErrorPreamble : manyErrorsPreamble;
        //noinspection ConstantConditions
        ret = TextUtil.append(ret, sep).append(trail).append(msg);
        sep = "\n";
        trail = childrenTrail;
      }
    }
    if (ret != null) {
      StringBuilder sb = ret;
      ret = new StringBuilder(ret.length()*2);
      ret.append(errorPreamble).append(sb);
    }
    return ret == null ? null : ret.toString();
  }

  @NotNull
  public CompositeDependentMkVerifier add(@NotNull ModelKeyVerifier verifier) {
    if (!myVerifiers.add(verifier)) {
      assert false : verifier;
    }
    return this;
  }

  private String getErrorPreamble(boolean oneProblem, @Nullable PropertyMap oldState, @NotNull PropertyMap newState) {
    final String mainKeyName = Util.NN(myMainKey.getDisplayableName());
    assert !mainKeyName.isEmpty();
    ItemKey mainValue = myMainKey.getValue(newState);
    boolean changedMainValue = oldState != null && !Util.equals(myMainKey.getValue(oldState), mainValue);
    String mainValueDisp = mainValue != null ? mainValue.getDisplayName() : null;
    return oneProblem
      ? (mainValue != null
          ? (changedMainValue
              ? "You have changed " + mainKeyName + " to " + mainValueDisp + ", which"
              : English.capitalize(mainKeyName) + ' ' + mainValueDisp
            ) + " does not permit selected value for field "
          : ("No " + mainKeyName + " is selected, value may be illegal for " + mainKeyName + "-dependent field ")
         )
      : (mainValue != null
          ? (changedMainValue
              ? "You have changed " + mainKeyName + " to " + mainValueDisp + ", values in following fields became invalid:"
              : "Following fields contain values that do not exist in selected " + Util.lower(mainKeyName) + ' ' + mainValueDisp + ':'
            )
          : "No " + mainKeyName + " is selected, values may be illegal for " + mainKeyName + "-dependent fields:"
         ) + "\n" + indent
    ;
  }

  @Override
  public String toString() {
    return "CDMKV(" + myMainKey + ')' + myVerifiers;
  }
}