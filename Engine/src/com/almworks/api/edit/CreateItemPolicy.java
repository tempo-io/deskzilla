package com.almworks.api.edit;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.MetaInfo;
import com.almworks.api.engine.Connection;
import com.almworks.items.api.DBItemType;
import com.almworks.items.api.DBReader;
import com.almworks.util.Pair;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.*;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.util.Collection;

/**
 * @author dyoma
 */
public interface CreateItemPolicy<D> {
  @ThreadAWT
  void update(UpdateContext context) throws CantPerformException;

  @ThreadAWT
  D prepareContextData(ActionContext context) throws CantPerformException;

  @Nullable
  @CanBlock
  Pair<Long, ? extends ItemCreator> newCreator(D contextData, Configuration config, DBReader reader);

  CreateItemPolicy<Connection> DEFAULT = new DefaultCreateItemPolicy();


  public static class DefaultCreateItemPolicy implements CreateItemPolicy<Connection> {
    public void update(UpdateContext context) throws CantPerformException {
    }

    public Connection prepareContextData(ActionContext context) throws CantPerformException {
      return DBDataRoles.findConnection(context);
    }

    public static long getPrototype(Connection connection) {
      Collection<DBItemType> primaryTypes = connection.getPrimaryTypes();
      if (primaryTypes.isEmpty()) {
        assert false;
        return 0L;
      }
      long prototype = connection.getPrototypeItem(primaryTypes.iterator().next());
      assert prototype > 0L : prototype;
      return prototype;
    }

    public Pair<Long, ? extends ItemCreator> newCreator(Connection connection, final Configuration config, DBReader reader) {
      final long prototype = getPrototype(connection);
      if (prototype <= 0L) {
        assert false;
        return null;
      }
      final MetaInfo metaInfo = MetaInfo.REGISTRY.getMetaInfo(prototype, reader);
      if (metaInfo == null) {
        assert false;
        return null;
      }
      ItemCreator creator = ThreadGate.AWT_IMMEDIATE.compute(new Computable<ItemCreator>() {
        public ItemCreator compute() {
          return metaInfo.newCreator(config);
        }
      });
      return Pair.create(prototype, creator);
    }
  }
}
