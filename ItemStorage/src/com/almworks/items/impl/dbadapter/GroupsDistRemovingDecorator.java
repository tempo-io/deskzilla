package com.almworks.items.impl.dbadapter;

import com.almworks.integers.IntList;
import com.almworks.integers.util.IntListRemovingDecorator;
import com.almworks.integers.util.ReadonlyIntListRemovingDecorator;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Decorates removal of groupIDs for items from the distribution fo items between groups.
 * @author igor baltiyskiy
 */
public class GroupsDistRemovingDecorator extends AbstractGroupsDist {
  private final List<IntListRemovingDecorator> myDecoratedGroups;

  private GroupsDistRemovingDecorator(List<IntListRemovingDecorator> decoratedGroups) {
    myDecoratedGroups = decoratedGroups;
  }

  @NotNull
  protected List<? extends IntList> getBackingList() {
    return myDecoratedGroups;
  }

  /**
   * Creates a removing decorator for GroupsDist.
   * @param groups GroupsDist to be decorated
   * @param preparedIndices prepared list of removed items indices (see {@link IntListRemovingDecorator#prepareSortedIndicesInternal}). They are not changed.
   * @return removing decorator for GroupsDist
   */
  public static GroupsDistRemovingDecorator create(@NotNull AbstractGroupsDist groups, @NotNull IntList preparedIndices) {
    assert groups != null;
    assert preparedIndices != null;
    List<IntListRemovingDecorator> decoratedGroups = new ArrayList(groups.groupingsCount());
    for (int i = 0; i < groups.groupingsCount(); ++i) {
      decoratedGroups.add(ReadonlyIntListRemovingDecorator.createFromPrepared(groups.getGrouping(i), preparedIndices));
    }
    return new GroupsDistRemovingDecorator(decoratedGroups);
  }

  @NotNull
  public AbstractGroupsDist undecorate() {
    return defaultUndecorate(this);
  }
}
