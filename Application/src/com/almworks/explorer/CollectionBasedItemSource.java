package com.almworks.explorer;

import com.almworks.api.application.*;
import com.almworks.items.api.*;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressSource;
import com.almworks.util.threads.ThreadAWT;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Collections;

public class CollectionBasedItemSource<T extends ItemWrapper> implements ItemSource {
  private final Progress myProgress = new Progress();
  private final Collection<T> myItems;

  private boolean myFirstRun = true;

  private CollectionBasedItemSource(Collection<T> collection) {
    myItems = collection;
  }

  @ThreadAWT
  public void stop(@NotNull ItemsCollector collector) {
    //ignore
  }

  @ThreadAWT
  public void reload(@NotNull final ItemsCollector collector) {
    if (myFirstRun) {
      Database.require().readForeground(new ReadTransaction<Void>() {
        @Override
        public Void transaction(DBReader reader) throws DBOperationCancelledException {
          myProgress.setStarted();
          for (ItemWrapper itemWrapper : myItems) {
            collector.addItem(itemWrapper.getItem(), reader);
          }
          myProgress.setDone();
          return null;
        }
      });
      myFirstRun = false;
    }
  }

  public ProgressSource getProgress(ItemsCollector collector) {
    return myProgress;
  }

  public static CollectionBasedItemSource create(ItemWrapper wrapper) {
    return new CollectionBasedItemSource(Collections.singleton(wrapper));
  }

  public static CollectionBasedItemSource create(Collection<? extends ItemWrapper> wrapperCollection) {
    return new CollectionBasedItemSource(Collections15.unmodifiableListCopy(wrapperCollection));
  }
}
