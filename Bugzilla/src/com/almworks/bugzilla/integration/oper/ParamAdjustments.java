package com.almworks.bugzilla.integration.oper;

import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.Collections15;

import java.util.Collection;
import java.util.Set;

public class ParamAdjustments {
  private final MultiMap<String, String> myAdd = MultiMap.create();
  private final MultiMap<String, String> myRemove = MultiMap.create();
  private final MultiMap<String, String> myReplace = MultiMap.create();
  private final Set<String> myClear = Collections15.hashSet();

  public void add(String name, String value) {
    myAdd.add(name, value);
  }

  public void add(String name, Collection<String> values) {
    myAdd.addAll(name, values);
  }

  public void remove(String name, String value) {
    myRemove.add(name, value);
  }

  public void clear(String name) {
    myClear.add(name);
  }

  public void replace(String name, String value) {
    myReplace.add(name, value);
  }

  public void replace(String name, Collection<String> values) {
    myReplace.addAll(name, values);
  }

  public MultiMap<String, String> adjust(MultiMap<String, String> defaults) {
    final MultiMap<String, String> result = MultiMap.create();
    if(defaults != null) {
      result.addAll(defaults);
    }
    for(final String name : myClear) {
      result.removeAll(name);
    }
    for(final Pair<String, String> pair : myRemove) {
      result.remove(pair.getFirst(), pair.getSecond());
    }
    for(final String name : myReplace.keySet()) {
      result.replaceAll(name, myReplace.getAll(name));
    }
    for(final String name : myAdd.keySet()) {
      result.addAll(name, myAdd.getAll(name));
    }
    return result;
  }

  public ParamAdjustments copy() {
    final ParamAdjustments copy = new ParamAdjustments();
    copy.myAdd.addAll(myAdd);
    copy.myRemove.addAll(myRemove);
    copy.myReplace.addAll(myReplace);
    copy.myClear.addAll(myClear);
    return copy;
  }

  public String getReplacement(String name) {
    return myReplace.getLast(name);
  }

  public void forgetReplacement(String name) {
    myReplace.removeAll(name);
  }
}
