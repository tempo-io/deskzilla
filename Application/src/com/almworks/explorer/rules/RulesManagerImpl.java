package com.almworks.explorer.rules;

import com.almworks.api.application.NameResolver;
import com.almworks.api.application.qb.FilterNode;
import com.almworks.api.engine.*;
import com.almworks.api.explorer.rules.ResolvedRule;
import com.almworks.api.explorer.rules.RulesManager;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.model.CollectionModelEvent;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Map;

/**
 * @author dyoma
 */
public class RulesManagerImpl implements RulesManager {
  private final Map<Connection, Map<FilterNode, ResolvedRule>> myResolvedStates = Collections15.hashMap();
  private final Engine myEngine;
  private final NameResolver myNameResolver;

  public RulesManagerImpl(Engine engine, NameResolver nameResolver) {
    myEngine = engine;
    myNameResolver = nameResolver;
    ConnectionManager connectionManager = myEngine.getConnectionManager();
    connectionManager.getConnections().getEventSource().addStraightListener(new CollectionModel.Adapter<Connection>() {
      public void onScalarsRemoved(CollectionModelEvent<Connection> event) {
        Collection<Connection> connections = event.getCollection();
        synchronized (myResolvedStates) {
          for (Connection connection : connections)
            myResolvedStates.remove(connection);
        }
      }
    });
  }

  @Nullable
  public ResolvedRule getResolvedRule(@NotNull Connection connection, FilterNode stateKey) {
    ResolvedRule rule = getCachedRule(connection, stateKey);
    if (rule != null)
      rule.normalize(myNameResolver);
    return rule;
  }

  private ResolvedRule getCachedRule(Connection connection, FilterNode stateKey) {
    synchronized (myResolvedStates) {
      Map<FilterNode, ResolvedRule> connectionMap = myResolvedStates.get(connection);
      if (connectionMap == null) {
        connectionMap = Collections15.hashMap();
        myResolvedStates.put(connection, connectionMap);
      }
      ResolvedRule state = connectionMap.get(stateKey);
      if (state != null)
        return state;
      FilterNode copy = stateKey.createCopy();
      state = new ResolvedRule(copy, connection);
      connectionMap.put(stateKey, state);
      return state;
    }
  }
}
