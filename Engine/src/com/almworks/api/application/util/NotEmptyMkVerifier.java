package com.almworks.api.application.util;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelKeyVerifier;
import com.almworks.util.English;
import com.almworks.util.commons.Function;
import com.almworks.util.properties.PropertyMap;
import org.jetbrains.annotations.*;

public class NotEmptyMkVerifier<T> implements ModelKeyVerifier {
  private final ModelKey<T> myKey;
  private final Function<? super T, Boolean> myIsEmpty;

  private NotEmptyMkVerifier(@NotNull ModelKey<T> key, @NotNull Function<? super T, Boolean> isEmpty) {
    myKey = key;
    myIsEmpty = isEmpty;
  }

  public static NotEmptyMkVerifier<String> forStringKey(ModelKey<String> key) {
    return new NotEmptyMkVerifier<String>(key, new Function<String, Boolean>() {
      @Override
      public Boolean invoke(String argument) {
        return argument != null && !argument.isEmpty();
      }
    });
  }

  public static <T> NotEmptyMkVerifier<T> create(ModelKey<T> key, Function<? super T, Boolean> isEmpty) {
    return new NotEmptyMkVerifier<T>(key, isEmpty);
  }

  @Override
  public String verify(@NotNull PropertyMap item) {
    Boolean valid = myIsEmpty.invoke(myKey.getValue(item));
    if (valid == null || !valid) {
      String keyName = myKey.getDisplayableName();
      if (keyName.isEmpty()) keyName = "Value";
      return English.capitalize(keyName) + " cannot be empty";
    }
    return null;
  }

  @Override
  public String verifyEdit(@Nullable PropertyMap oldState, @NotNull PropertyMap newState) {
    Boolean wasValid = (oldState == null) ? Boolean.TRUE : myIsEmpty.invoke(myKey.getValue(oldState));
    String error = verify(newState);
    return error != null && (wasValid == Boolean.TRUE) ? error : null;
  }

  @Override
  public String toString() {
    return "NEMKV(" + myKey + ')';
  }
}
