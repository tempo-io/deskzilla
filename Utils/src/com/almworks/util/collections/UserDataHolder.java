package com.almworks.util.collections;

import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.Map;

/**
 * @author dyoma
 */
public class UserDataHolder {
  private final Map<TypedKey<?>, Object> myUserData = Collections15.hashMap();

  public <T> T getUserData(TypedKey<T> key) {
    synchronized(getUserDataLock()) {
      return key.getFrom(myUserData);
    }
  }

  public <T> void putUserDate(TypedKey<T> key, T data) {
    synchronized(getUserDataLock()) {
      if (data == null)
        myUserData.remove(key);
      else
        key.putTo(myUserData, data);
    }
  }

  public Object getUserDataLock() {
    return myUserData;
  }
}
