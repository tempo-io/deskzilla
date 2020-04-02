package com.almworks.api.engine;

import com.almworks.util.config.*;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.ThreadAWT;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
public interface ItemProvider {
  public static final Role<ItemProvider> ROLE = Role.role(ItemProvider.class);

  Connection createConnection(String connectionID, ReadonlyConfiguration configuration, boolean isNew) throws ConfigurationException, ProviderDisabledException;

  String getProviderID();

  String getProviderName();

  void showNewConnectionWizard();

  void showEditConnectionWizard(Connection connection);

  boolean isEditingConnection(Connection connection);

  ScalarModel<ItemProviderState> getState() throws ProviderDisabledException;

  @Nullable
  Configuration createDefaultConfiguration(String itemUrl) throws ProviderDisabledException;

  @ThreadAWT
  boolean isItemUrl(String url) throws ProviderDisabledException;

  boolean isEnabled();

  ProviderActivationAgent createActivationAgent();

  @Nullable
  Configuration getConnectionConfig(String connectionID);

  PrimaryItemStructure getPrimaryStructure();

  @Nullable
  String getDisplayableItemIdFromUrl(String url);
}
