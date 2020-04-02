package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * This class is needed to update ModelMap by 3 model keys at once.
 * This class can be used, when list not always exists and
 * we need to keep boolean for contain-determination and count to keep size and
 * we need to have three values syncronized
 *
 * @author Alex
 */
public final class EditUserList {
  /**
   * Is current user included in the list
   */
  private final ModelKey<Boolean> mySelfKey;

  /**
   * Full list
   */
  private final ModelKey<List<ItemKey>> myListKey;

  /**
   * Count of full list
   */
  private final ModelKey<Integer> myCountKey;

  public EditUserList(ModelKey<Boolean> selfKey, ModelKey<List<ItemKey>> listKey, ModelKey<Integer> countKey) {
    mySelfKey = selfKey;
    myListKey = listKey;
    myCountKey = countKey;
  }

  public void toggleMe(@Nullable ItemKey me, ModelMap map)  {
    if (me != null) {
      boolean self = adjustSelf(map);
      List<ItemKey> list = adjustList(me, map, self);
      adjustCount(map, self, list);
    }
  }

  private void adjustCount(ModelMap map, boolean self, @Nullable List<ItemKey> list) {
    Integer count;
    if (list != null) {
      count = list.size();
    } else {
      count = myCountKey.getValue(map);
      if (count != null) {
        count = self ? count + 1 : count - 1;
        if (count < 0) {
          assert false : count;
          count = 0;
        }
      }
    }
    if (count != null) {
      updateModelMap(myCountKey, map, count);
    }
  }

  private boolean adjustSelf(ModelMap map) {
    Boolean curIsInclude = mySelfKey.getValue(map);
    if (curIsInclude == null) {
      assert false : map;
      return false;
    }
    boolean isInclude = !curIsInclude;
    updateModelMap(mySelfKey, map, isInclude);
    return isInclude;
  }

  private static <T> void updateModelMap(ModelKey<T> isWatchingkey, ModelMap map, T value) {
    PropertyMap propertyMap = new PropertyMap();
    isWatchingkey.takeSnapshot(propertyMap, map);
    isWatchingkey.setValue(propertyMap, value);
    isWatchingkey.copyValue(map, propertyMap);
  }

  private List<ItemKey> adjustList(@NotNull ItemKey me, ModelMap map, boolean self) {
    List<ItemKey> value = myListKey.getValue(map);
    if (value == null)
      return null;
    List<ItemKey> users = Collections15.arrayList(value);
    boolean changed = false;
    if (self) {
      if (!users.contains(me)) {
        users.add(me);
        changed = true;
      }
    } else {
      if (users.contains(me)) {
        users.remove(me);
        changed = true;
      }
    }
    if (changed) {
      updateModelMap(myListKey, map, users);
    }
    return users;
  }
}
