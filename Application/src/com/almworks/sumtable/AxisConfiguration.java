package com.almworks.sumtable;

import com.almworks.api.application.NameResolver;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.tree.QueryResult;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.ListModelHolder;
import com.almworks.util.collections.Modifiable;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.config.Configuration;
import com.almworks.util.events.DetachableValue;
import com.almworks.util.exec.Context;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

class AxisConfiguration {
  private static final String DEFINITION_TYPE = "type";
  private static final String DEFINITION_TYPE_ENUM = "enum";
  private static final String ENUM_DESCRIPTOR_ID = "id";
  private static final String SORT_DIRECTION = "sortDirection";
  private static final String SORT_BY = "sortBy";

  private final SimpleModifiable myDefinitionModifiable = new SimpleModifiable();
  private final SimpleModifiable mySortingModifiable = new SimpleModifiable();

  private final Configuration myConfig;

  private final ListModelHolder<STFilter> myModel = ListModelHolder.create();
  private final Lifecycle myModelLife = new Lifecycle();
  private final DetachableValue<QueryResult> myQueryResult = DetachableValue.create();

  private AxisDefinition myAxisDefinition;

  /**
   * 0 - no sorting
   * 1 - ascending
   * -1 - descending
   */
  private int mySortDirection;

  @Nullable
  private String mySortBy;

  private STFilter mySortFilter;

  public AxisConfiguration(Configuration config) {
    myConfig = config;
    mySortDirection = config.getIntegerSetting(SORT_DIRECTION, 0);
    mySortBy = config.getSetting(SORT_BY, null);
    if (STFilter.TOTAL.getId().equals(mySortBy)) {
      mySortFilter = STFilter.TOTAL;
      mySortBy = null;
    }
  }

  public void attach(Lifespan lifespan, QueryResult queryResult) {
    myQueryResult.set(lifespan, queryResult);
    myAxisDefinition = load(myConfig, queryResult);
    updateModel();
    myDefinitionModifiable.fireChanged();
  }

  public AxisDefinition getAxisDefinition() {
    return myAxisDefinition;
  }

  public Modifiable getDefinitionModifiable() {
    return myDefinitionModifiable;
  }

  public SimpleModifiable getSortingModifiable() {
    return mySortingModifiable;
  }

  public void setAxisDefinition(AxisDefinition definition) {
    if (!Util.equals(myAxisDefinition, definition)) {
      myAxisDefinition = definition;
      save();
      updateModel();
      myDefinitionModifiable.fireChanged();
    }
  }

  private void updateModel() {
    myModelLife.cycle();
    QueryResult queryResult = myQueryResult.get();
    if (queryResult == null) {
      myModel.setModel(null);
    } else {
      AListModel<? extends STFilter> model;
      if (myAxisDefinition != null) {
        model = myAxisDefinition.createOptionsModel(myModelLife.lifespan(), queryResult.getEncompassingHypercube());
      } else {
        model = AListModel.EMPTY;
      }
      myModel.setModel(model);
    }
  }

  private static AxisDefinition load(Configuration config, QueryResult queryResult) {
    String type = config.getSetting(DEFINITION_TYPE, null);
    if (DEFINITION_TYPE_ENUM.equalsIgnoreCase(type)) {
      return loadEnumDefinition(config, queryResult);
    } else {
      return null;
    }
  }

  private static EnumAxisDefinition loadEnumDefinition(Configuration config, QueryResult queryResult) {
    String id = config.getSetting(ENUM_DESCRIPTOR_ID, null);
    if (id == null)
      return null;
    NameResolver resolver = Context.require(NameResolver.ROLE);
    ConstraintDescriptor descriptor = resolver.getConditionDescriptor(id, queryResult.getEncompassingHypercube());
    if (descriptor == null)
      return null;
    return new EnumAxisDefinition(descriptor);
  }

  private void save() {
    myConfig.clear();
    if (myAxisDefinition instanceof EnumAxisDefinition) {
      myConfig.setSetting(DEFINITION_TYPE, DEFINITION_TYPE_ENUM);
      myConfig.setSetting(ENUM_DESCRIPTOR_ID, ((EnumAxisDefinition) myAxisDefinition).getDescriptor().getId());
    }
    if (mySortFilter != null)
      myConfig.setSetting(SORT_BY, mySortFilter.getId());
    else if (mySortBy != null)
      myConfig.setSetting(SORT_BY, mySortBy);
    if (mySortDirection != 0)
      myConfig.setSetting(SORT_DIRECTION, mySortDirection);
  }

  public AListModel<STFilter> getOptionsModel() {
    return myModel;
  }

  public int getSortDirection() {
    return mySortDirection;
  }

  public STFilter getSortFilter() {
    STFilter filter = mySortFilter;
    if (filter == STFilter.TOTAL)
      return filter;

    if (filter == null) {
      String sortBy = mySortBy;
      if (sortBy != null) {
        for (int i = 0; i < myModel.getSize(); i++) {
          filter = myModel.getAt(i);
          if (sortBy.equals(filter.getId())) {
            mySortFilter = filter;
            return filter;
          }
        }
      }
      return null;
    }

    if (!myModel.contains(filter))
      return null;
    else
      return filter;
  }

  public void applySorting(STFilter filter) {
    if (filter != STFilter.TOTAL && !myModel.contains(filter)) {
      assert false : myModel + " " + filter;
      return;
    }
    STFilter currentSortFilter = getSortFilter();
    if (Util.equals(currentSortFilter, filter)) {
      mySortDirection = -mySortDirection;
    } else {
      mySortFilter = filter;
      mySortBy = null;
      mySortDirection = -1;
    }
    save();
    mySortingModifiable.fireChanged();
  }

  public void setSorting(STFilter filter, int direction) {
    mySortFilter = filter;
    mySortDirection = direction;
    mySortBy = null;
    save();
    mySortingModifiable.fireChanged();
  }
}
