package com.almworks.api.explorer.util;

import com.almworks.api.application.*;
import com.almworks.api.application.util.ModelKeyUtils;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.spi.provider.AbstractConnection;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.config.Configuration;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
*/
public class ConnectContext {
  private final Lifespan myLife;
  private final ModelMap myModel;
  @Nullable
  private final Connection myConnection;

  public ConnectContext(Lifespan life, ModelMap model) {
    this(life, model, null);
  }

  public ConnectContext(Lifespan life, ModelMap model, Connection connection) {
    myLife = life;
    myModel = model;
    myConnection = connection;
  }

  public Lifespan getLife() {
    return myLife;
  }

  public ModelMap getModel() {
    return myModel;
  }

  public <T> T getValue(ModelKey<T> key) {
    return key.getValue(myModel);
  }

  public void attachModelListener(ChangeListener listener) {
    myModel.addAWTChangeListener(myLife, listener);
    listener.onChange();
  }

  public <T> void updateModel(ModelKey<T> key, T value) {
    ModelKeyUtils.setModelValue(key, value, myModel);
  }

  @Nullable
  public Connection getConnection() {
    LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(myModel);
    return lis != null ? lis.getConnection() : myConnection;
  }

  public ItemHypercube getDefaultCube() {
    return UIControllerUtil.DEFAULT_CUBE_CONVERTOR.convert(myModel);
  }

  public Configuration getRecentConfig(String id) {
    Connection connection = getConnection();
    if (!(connection instanceof AbstractConnection)) return Configuration.EMPTY_CONFIGURATION;
    return ((AbstractConnection) connection).getConnectionConfig(AbstractConnection.RECENTS, id);
  }

  public static class Accessor<T> implements com.almworks.util.collections.Accessor<ConnectContext, T> {
    private final ModelKey<T> myKey;

    public Accessor(ModelKey<T> key) {
      myKey = key;
    }

    public T getValue(ConnectContext context) {
      return context.getValue(myKey);
    }

    public void setValue(ConnectContext context, T value) {
      context.updateModel(myKey, value);
    }
  }
}
