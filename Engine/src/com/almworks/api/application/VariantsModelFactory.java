package com.almworks.api.application;

import com.almworks.util.advmodel.AListModel;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @author dyoma
 */
public interface VariantsModelFactory {
  AListModel<ItemKey> createModel(Lifespan life);

  /** @return unsorted list of resolved items */
  @NotNull
  List<ResolvedItem> getResolvedList();
}
