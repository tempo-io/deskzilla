package com.almworks.api.explorer;

import com.almworks.api.application.ItemsCollector;
import com.almworks.api.application.util.sources.AbstractItemSource;
import com.almworks.items.api.*;
import com.almworks.util.progress.Progress;
import org.almworks.util.TypedKey;

import java.util.Collection;
import java.util.Iterator;

public abstract class SimpleCollection extends AbstractItemSource {
  private final TypedKey<Boolean> STOP_KEY = key(Boolean.class);

  protected SimpleCollection() {
    super(SimpleCollection.class.getName());
  }

  public static SimpleCollection create(final Collection<Long> items) {
    return new SimpleCollection() {
      @Override
      protected Iterator<Long> getItems() {
        return items.iterator();
      }
    };
  }

  public void stop(ItemsCollector collector) {
    collector.putValue(STOP_KEY, Boolean.TRUE);
  }

  public void reload(final ItemsCollector collector) {
    Boolean prevValue = collector.putValue(STOP_KEY, Boolean.FALSE);
    assert prevValue == null : prevValue;

    getProgressDelegate(collector).delegate(new Progress(this.toString()));

    Database.require().readForeground(new ReadTransaction<Object>() {
      public Object transaction(DBReader reader) {
        Iterator<Long> items = getItems();
        while (items.hasNext()) {
          Long item = items.next();
          collector.addItem(item, reader);
          if (collector.getValue(STOP_KEY).booleanValue())
            return null;
        }
        getProgressDelegate(collector).setDone();
        return null;
      }
    });
  }

  protected abstract Iterator<Long> getItems();
}
