package com.almworks.spi.provider;

import com.almworks.api.application.field.ConnectionFieldsManager;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.download.DownloadOwner;
import com.almworks.api.engine.*;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.Map;

public interface ConnectionContext {
  ScalarModel<ConnectionState> getState();

  @NotNull
  ComponentContainer getContainer();

  Connection getConnection();

  void start();

  String getLastInitializationError();

  void stop();

  ScalarModel<InitializationState> getInitializationState();

  void setInitializationInProgress();

  void setInitializationResult(boolean success, String error);

  <K, V> Map<K, V> getConnectionWideCache(TypedKey<Map<K, V>> key);

  DownloadOwner getDownloadOwner();

  void requestReinitialization();

  ConnectionFieldsManager getCustomFields();
}