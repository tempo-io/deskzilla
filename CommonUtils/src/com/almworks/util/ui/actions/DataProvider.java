package com.almworks.util.ui.actions;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Condition;
import com.almworks.util.ui.ComponentProperty;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

/**
 * @author dyoma
 */
public interface DataProvider {
  ComponentPropertyKey DATA_PROVIDER = new ComponentPropertyKey();

  @Nullable
    <T> List<T> getObjectsByRole(TypedKey<? extends T> role);

  boolean hasRole(@NotNull TypedKey<?> role);

  void addRoleListener(Lifespan life, TypedKey role, ChangeListener listener);

  @Nullable
  JComponent getSourceComponent(TypedKey<?> role);

  @NotNull
  Collection<? extends TypedKey> getCurrentlyAvailableRoles();

  /**
   * @deprecated
   */
  class NoDataException extends Exception {
    public NoDataException() {
    }

    public NoDataException(String message) {
      super(message);
    }
  }

  public static class ComponentPropertyKey extends ComponentProperty.Simple<DataProvider> {
    public ComponentPropertyKey() {
      super("dataProvider");
    }

    public void putClientValue(JComponent component, DataProvider provider) {
      DataProvider prev = getClientValue(component);
      if (prev != null) {
        CompositeDataProvider composite;
        if (prev instanceof CompositeDataProvider)
          composite = (CompositeDataProvider) prev;
        else {
          composite = new CompositeDataProvider();
          composite.addProvider(prev);
        }
        composite.addProvider(provider);
        provider = composite;
      }
      super.putClientValue(component, provider);
    }

    public void removeAllProviders(JComponent component) {
      component.putClientProperty(this, null);
    }

    @NotNull
    public Collection<? extends DataProvider> getAllProviders(JComponent component) {
      DataProvider provider = DATA_PROVIDER.getClientValue(component);
      if (provider == null)
        return Collections15.emptyCollection();
      if (!(provider instanceof CompositeDataProvider))
        return Collections.singleton(provider);
      return ((CompositeDataProvider) provider).getProviders();
    }

    public void removeProviders(JComponent component, Condition<DataProvider> condition) {
      DataProvider provider = DATA_PROVIDER.getClientValue(component);
      if (provider == null)
        return;
      if (!(provider instanceof CompositeDataProvider)) {
        if (condition.isAccepted(provider))
          removeAllProviders(component);
      } else
        ((CompositeDataProvider) provider).removeProviders(condition);
    }
  }
}
