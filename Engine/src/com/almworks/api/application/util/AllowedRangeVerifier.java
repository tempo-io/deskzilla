package com.almworks.api.application.util;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelKeyVerifier;
import com.almworks.util.English;
import com.almworks.util.collections.Containers;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Function;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.almworks.util.Collections15.hashSet;

/**
 * Verifies that model key values are in the allowed range.
 */
public class AllowedRangeVerifier<T> implements ModelKeyVerifier {
  private final ModelKey<? extends Collection<T>> myKey;
  private final Function<PropertyMap, Set<T>> myAllowedValues;
  private final Convertor<T, String> myToString;
  private final String myDisallowedTerm;

  private AllowedRangeVerifier(ModelKey<? extends Collection<T>> key, Function<PropertyMap, ? extends Set<? extends T>> allowedValues,
    Convertor<T, String> toString, String disallowedTerm) {
    myAllowedValues = (Function<PropertyMap, Set<T>>)allowedValues;
    myToString = toString;
    myKey = key;
    myDisallowedTerm = English.capitalize(disallowedTerm);
  }

  /**
   * @param allowedValues returns allowed values in the context of the specified property map. Can return null, this means that every value is allowed

   */
  public static <T> AllowedRangeVerifier<T> create(ModelKey<? extends Collection<T>> key, Function<PropertyMap, ? extends Set<? extends T>> allowedValues,
    Convertor<T, String> toString, String disallowedTerm) {
    return new AllowedRangeVerifier<T>(key, allowedValues, toString, disallowedTerm);
  }

  @Override
  public String verify(@NotNull PropertyMap item) {
    return createErrorMessage(disallowedValues(item));
  }

  @Override
  public String verifyEdit(@Nullable PropertyMap oldState, @NotNull PropertyMap newState) {
    if (oldState == null) return verify(newState);
    return createErrorMessage(Containers.complement(disallowedValues(newState), disallowedValues(oldState)));
  }

  @Nullable
  private String createErrorMessage(Collection<String> disallowed) {
    int errSz = disallowed.size();
    if (errSz == 0) return null;
    String keyTerm = Util.lower(Util.NN(myKey.getDisplayableName(), "value"));
    return myDisallowedTerm + ' ' + keyTerm +  ": " + TextUtil.separate(disallowed, ", ");
  }

  @NotNull
  private Set<String> disallowedValues(@Nullable PropertyMap item) {
    if (item == null) {
      return emptySet();
    }
    Set<T> allowed = myAllowedValues.invoke(item);
    if (allowed == null) return emptySet();
    Set<T> current = hashSet(myKey.getValue(item));
    current.removeAll(allowed);
    return myToString.collectSet(current);
  }

}
