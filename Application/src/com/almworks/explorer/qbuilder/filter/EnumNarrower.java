package com.almworks.explorer.qbuilder.filter;

import com.almworks.api.application.ResolvedItem;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.CollectionModel;
import com.almworks.util.model.CollectionModelEvent;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public interface EnumNarrower {
  EnumNarrower DEFAULT = new ConnectionFilteringNarrower();
  EnumNarrower IDENTITY = new IdentityNarrower();

  AListModel<? extends ResolvedItem> narrowModel(Lifespan life, AListModel<? extends ResolvedItem> original,
    ItemHypercube cube);

  List<ResolvedItem> narrowList(List<ResolvedItem> values, ItemHypercube cube);


  abstract class Filtering<T> implements EnumNarrower {
    public AListModel<? extends ResolvedItem> narrowModel(Lifespan life,
      AListModel<? extends ResolvedItem> original, final ItemHypercube cube)
    {
      final T data = getNarrowDownData(cube);
      if (data == null)
        return original;
      final FilteringListDecorator<? extends ResolvedItem> result = FilteringListDecorator.create(life, original);
      ThreadGate.AWT.execute(new Runnable() {
        public void run() {
          result.setFilter(new Condition<ResolvedItem>() {
            public boolean isAccepted(ResolvedItem value) {
              return Filtering.this.isAccepted(value, data, cube);
            }
          });
        }
      });
      return result;
    }

    @Nullable
    protected abstract T getNarrowDownData(ItemHypercube cube);

    protected abstract boolean isAccepted(ResolvedItem artifact, @NotNull T data, ItemHypercube cube);

    public List<ResolvedItem> narrowList(List<ResolvedItem> values, ItemHypercube cube) {
      T data = getNarrowDownData(cube);
      if (data == null)
        return values;
      List<ResolvedItem> result = Collections15.arrayList();
      for (ResolvedItem artifact : values) {
        if (isAccepted(artifact, data, cube))
          result.add(artifact);
      }
      return result;
    }
  }


  class AggregatingNarrower implements EnumNarrower {
    private final Convertor<Connection, EnumNarrower> myConvertor;

    public AggregatingNarrower(Convertor<Connection, EnumNarrower> convertor) {
      myConvertor = convertor;
    }

    public List<ResolvedItem> narrowList(List<ResolvedItem> values, ItemHypercube cube) {
      Engine engine = getEngine();
      Collection<Connection> connections = engine.getConnectionManager().getReadyConnectionsModel().copyCurrent();
      List<ResolvedItem> result = Collections15.arrayList();
      Set<ResolvedItem> resultSet = Collections15.hashSet();
      boolean narrowedByConnection = cube.containsAxis(SyncAttributes.CONNECTION);
      for (Connection connection : connections) {
        boolean allowed = !narrowedByConnection || cube.allows(SyncAttributes.CONNECTION, connection.getConnectionItem());
        if (allowed) {
          EnumNarrower narrower = myConvertor.convert(connection);
          List<ResolvedItem> narrowed = narrower.narrowList(values, cube.copy());
          for (ResolvedItem artifact : narrowed)
            if (!resultSet.contains(artifact)) {
              resultSet.add(artifact);
              result.add(artifact);
            }
        }
      }
      return result;
    }

    private Engine getEngine() {
      return Context.require(Engine.ROLE);
    }

    public AListModel<? extends ResolvedItem> narrowModel(final Lifespan life,
      final AListModel<? extends ResolvedItem> original, ItemHypercube cube)
    {
      final Engine engine = getEngine();
      final AListAggregator<ResolvedItem> aggregator = AListAggregator.create();
      final ItemHypercube cubeCopy = cube.copy();
      final boolean narrowed = cubeCopy.containsAxis(SyncAttributes.CONNECTION);
      CollectionModel<Connection> connections = engine.getConnectionManager().getReadyConnectionsModel();
      connections.getEventSource().addAWTListener(life, new CollectionModel.Adapter<Connection>() {
        public void onScalarsAdded(CollectionModelEvent<Connection> event) {
          if (life.isEnded())
            return;
          for (Connection connection : event.getCollection()) {
            boolean allowed = !narrowed || cubeCopy.allows(SyncAttributes.CONNECTION, connection.getConnectionItem());
            if (allowed) {
              EnumNarrower connectionNarrower = myConvertor.convert(connection);
              AListModel<? extends ResolvedItem> narrowedConnection =
                connectionNarrower.narrowModel(life, original, cubeCopy);
              aggregator.add(life, connection, narrowedConnection);
            }
          }
        }

        public void onScalarsRemoved(CollectionModelEvent<Connection> event) {
          if (life.isEnded())
            return;
          for (Connection connection : event.getCollection()) {
            aggregator.remove(connection);
          }
        }
      });
      return aggregator.getImage();
    }
  }


  public static class ConnectionFilteringNarrower extends Filtering<Collection<Long>> {
    protected Collection<Long> getNarrowDownData(ItemHypercube cube) {
      return cube.getIncludedValues(SyncAttributes.CONNECTION);
    }

    protected boolean isAccepted(ResolvedItem artifact, @NotNull Collection<Long> connections, ItemHypercube cube) {
      Long valueConnection = artifact.getConnectionItem();
      return valueConnection == null || connections.contains(valueConnection);
    }
  }


  public static class IdentityNarrower implements EnumNarrower {
    public AListModel<? extends ResolvedItem> narrowModel(Lifespan life,
      AListModel<? extends ResolvedItem> original, ItemHypercube cube)
    {
      return original;
    }

    public List<ResolvedItem> narrowList(List<ResolvedItem> values, ItemHypercube cube) {
      return values;
    }
  }
}
