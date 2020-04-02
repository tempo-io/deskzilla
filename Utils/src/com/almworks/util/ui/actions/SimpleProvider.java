package com.almworks.util.ui.actions;


import com.almworks.util.collections.Comparing;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class SimpleProvider extends AbstractDataProvider {
  private final Map<TypedKey<?>, List<?>> myData = Collections15.hashMap();

  public SimpleProvider(TypedKey<?>... roles) {
    super(roles);
  }

  public SimpleProvider(Collection<? extends TypedKey<?>> roles) {
    super(roles);
  }

  public <T> void setSingleData(@NotNull TypedKey<? super T> role, @Nullable T value) {
    setSingleData(role, value, false);
  }

  public <T> void setSingleData(@NotNull TypedKey<? super T> role, @Nullable T value, boolean forceFire) {
    if (value == null)
      removeData(role);
    setData(role, Collections.singletonList(value), forceFire);
  }

  public <T> void setData(TypedKey<? super T> role, List<T> value, boolean forceFire) {
    privateSetData(role, value, forceFire);
  }

  private <T> void privateSetData(TypedKey<? super T> role, List<T> value, boolean forceFire) {
    Threads.assertAWTThread();
    assert hasRole(role) : role;
    if (!forceFire && Comparing.areOrdersEqual(value, myData.get(role)))
      return;
    if (value != null)
      myData.put(role, value);
    else
      myData.remove(role);
    fireChanged(role);
  }

  public void removeData(TypedKey<?> role) {
    assert hasRole(role) : role;
    setData((TypedKey<? super Object>)  role, null, false);
  }

  public void removeAllData() {
    for (TypedKey<?> role : getRolesInternal()) {
      removeData(role);
    }
  }

  @Nullable
  public <T> T getSingleValue(TypedKey<? extends T> role) {
    List<T> values = getObjectsByRole(role);
    if (values == null)
      return null;
    if (values.size() != 1) {
      assert false : values;
    }
    return values.get(0);
  }

  public <T> List<T> getObjectsByRole(TypedKey<? extends T> role) {
    return (List<T>) myData.get(role);
  }

  public static <T> DataProvider withData(TypedKey<T> role, T data) {
    SimpleProvider provider = new SimpleProvider(role);
    provider.setSingleData(role, data);
    return provider;
  }
}
