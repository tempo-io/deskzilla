package com.almworks.util.ui.actions;

import com.almworks.util.collections.ChangeListener;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

/**
 * @author dyoma
 */
public class MockProvider implements DataProvider {
  private final TypedKey myRole;
  private final JComponent mySourceComponent;
  private Object myValue;
  private ChangeListener myListener;

  public MockProvider(TypedKey role, JComponent sourceComponent) {
    myRole = role;
    mySourceComponent = sourceComponent;
  }

  public MockProvider(TypedKey role) {
    this(role, null);
  }

  public <T> List<T> getObjectsByRole(TypedKey<? extends T> role) {
    if (!myRole.equals(role))
      return null;
    return (List<T>) (myValue != null ? Collections.singletonList(myValue) : Collections15.emptyList());
  }

  public boolean hasRole(TypedKey<?> role) {
    assert role != null;
    return myRole.equals(role);
  }

  public Collection<? extends TypedKey> getCurrentlyAvailableRoles() {
    return Collections.singleton(myRole);
  }

  public void addRoleListener(Lifespan life, TypedKey role, ChangeListener listener) {
    assert myListener == null : myListener;
    myListener = listener;
    life.add(new Detach() {
      protected void doDetach() {
        myListener = null;
      }
    });
  }

  @Nullable
  public JComponent getSourceComponent(TypedKey<?> role) {
    return mySourceComponent;
  }

  public void setValue(Object value) {
    myValue = value;
    if (myListener != null)
      myListener.onChange();
  }
}
