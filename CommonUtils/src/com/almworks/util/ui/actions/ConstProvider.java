package com.almworks.util.ui.actions;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.globals.GlobalData;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

/**
 * @author dyoma
 */
public class ConstProvider implements DataProvider {
  private final MultiMap<TypedKey, Object> myData = MultiMap.create();
  private JComponent mySourceComponent;
//  private final Map<TypedKey, List> myData = Collections15.hashMap();

  public <T> List<T> getObjectsByRole(TypedKey<? extends T> role) {
    return (List<T>) myData.getAll(role);
  }

  public boolean hasRole(TypedKey<?> role) {
    return myData.containsKey(role);
  }

  public Collection<? extends TypedKey> getCurrentlyAvailableRoles() {
    return Collections.unmodifiableSet(myData.keySet());
  }

  public void addRoleListener(Lifespan life, TypedKey role, ChangeListener listener) {
  }

  @Nullable
  public JComponent getSourceComponent(TypedKey<?> role) {
    return mySourceComponent;
  }

  public <T> ConstProvider addData(TypedKey<T> role, T value) {
    Threads.assertAWTThread();
    assert value != null : role;
    myData.add(role, value);
    return this;
  }

  public static <T> ConstProvider singleData(TypedKey<T> role, T value) {
    ConstProvider provider = new ConstProvider();
    provider.addData(role, value);
    return provider;
  }

  public static <T> void addRoleValue(JComponent component, TypedKey<T> role, T value) {
    ConstProvider provider = CompositeDataProvider.findProvider(ConstProvider.class, component);
    if (provider == null) {
      provider = new ConstProvider();
      DataProvider.DATA_PROVIDER.putClientValue(component, provider);
    }
    provider.addData(role, value);
    assert provider.mySourceComponent == component || provider.mySourceComponent == null :
      provider.mySourceComponent + ", " + component;
    provider.mySourceComponent = component;
  }

  public static <T> void addGlobalValue(JComponent component, TypedKey<T> role, T value) {
    addRoleValue(component, role, value);
    GlobalData.KEY.addClientValue(component, role);
  }
}
