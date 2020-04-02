package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.ConnectionState;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.ReferenceLink;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.integers.LongIterator;
import com.almworks.items.api.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Condition;
import com.almworks.util.model.*;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.ThreadAWT;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.util.*;

public class ProductDependenciesTracker implements Startable {
  public static final Role<ProductDependenciesTracker> ROLE = Role.role(ProductDependenciesTracker.class);

  private final ComponentContainer myContainer;
  private final PrivateMetadata myPrivateMetadata;
  private final DetachComposite myDetach = new DetachComposite();

  private final Map<ResolvedItem, ProductDependencyInfo> myProductDependencyMap =
    Collections.synchronizedMap(Collections15.<ResolvedItem, ProductDependencyInfo>hashMap());

  private final DetachComposite myStartingDetach = new DetachComposite(true);

  private final SimpleModifiable myModifiable = new SimpleModifiable();
  private final NameResolver myNameResolver;

  public ProductDependenciesTracker(ComponentContainer container, PrivateMetadata privateMetadata) {
    myContainer = container;
    myPrivateMetadata = privateMetadata;
    myNameResolver = container.getActor(NameResolver.ROLE);
    assert myNameResolver != null;
  }

  public void start() {
    BugzillaConnection connection = myContainer.getActor(BugzillaConnection.ROLE);
    assert connection != null;
    myStartingDetach.add(
      connection.getState().getEventSource().addStraightListener(new ScalarModel.Adapter<ConnectionState>() {
        public void onScalarChanged(ScalarModelEvent<ConnectionState> event) {
          if (event.getNewValue() == ConnectionState.READY) {
            initialize();
            myStartingDetach.detach();
          }
        }
      }));
  }

  private void initialize() {
    PrivateMetadata privateMetadata = myContainer.getActor(PrivateMetadata.ROLE);

    ReferenceLink link = (ReferenceLink) CommonMetadata.ATTR_TO_LINK.get(BugzillaAttribute.PRODUCT);
    link.getReferentsView(privateMetadata).liveQuery(myDetach, new DBLiveQuery.Listener() {
      @Override
      public void onICNPassed(long icn) {
      }

      @Override
      public void onDatabaseChanged(DBEvent event, DBReader reader) {
        for(final LongIterator it = event.getAddedAndChangedSorted().iterator(); it.hasNext();) {
          updateProduct(it.next(), reader);
        }
        for(final LongIterator it = event.getRemovedSorted().iterator(); it.hasNext();) {
          removeProduct(it.next(), reader);
        }
      }
    });
  }

  private synchronized void removeProduct(long item, DBReader reader) {
    ResolvedItem resolved = myNameResolver.getCache().getItemKeyOrNull(item, reader, Product.ENUM_PRODUCTS.getFactory());
    if (resolved == null)
      return;
    ProductDependencyInfo info = myProductDependencyMap.remove(resolved);
    if (info != null)
      info.removed();
    myModifiable.fireChanged();
  }

  private synchronized void updateProduct(long item, DBReader reader) {
    ResolvedItem resolved = myNameResolver.getCache().getItemKeyOrNull(item, reader, Product.ENUM_PRODUCTS.getFactory());
    if (resolved == null)
      return;
    ProductDependencyInfo info = myProductDependencyMap.get(resolved);
    if (info == null) {
      info = new ProductDependencyInfo(myNameResolver);
      myProductDependencyMap.put(resolved, info);
    }
    info.update(SyncUtils.readTrunk(reader, item));
    myModifiable.fireChanged();
  }

  public void stop() {
    myDetach.detach();
    myStartingDetach.detach();
  }

  @Nullable
  public List<ResolvedItem> narrowList(DBAttribute<Long> attribute, List<ResolvedItem> values, ItemHypercube cube) {
    Condition<ResolvedItem> filter =
      createNarrowingFilter(attribute, BasicScalarModel.createConstant(getProductsSubset(cube)), cube);
    if (filter == null)
      return null;
    return filter.filterList(values);
  }

  @Nullable
  @ThreadAWT
  public AListModel<? extends ResolvedItem> narrowEnums(Lifespan life, DBAttribute<Long> attribute,
    AListModel<? extends ResolvedItem> fullModel, ItemHypercube contextCube)
  {
    if (!contextCube.containsAnyAxis(Bug.attrProduct, Bug.attrComponent, Bug.attrVersion, Bug.attrTargetMilestone))
    {
      return null;
    }
    final ItemHypercube cube = contextCube.copy();
    final ScalarModel<Set<ResolvedItem>> products = createProductsModel(life, cube);
    final FilteringListDecorator<? extends ResolvedItem> result = FilteringListDecorator.create(fullModel);
    Condition<ResolvedItem> filter;
    filter = createNarrowingFilter(attribute, products, cube);
    if (filter == null)
      return null;
    result.setFilter(filter);
    life.add(result.getDetach());
    if (Bug.attrProduct.equals(attribute)) {
      products.getEventSource().addAWTListener(life, new ScalarModel.Adapter<Set<ResolvedItem>>() {
        public void onScalarChanged(ScalarModelEvent<Set<ResolvedItem>> event) {
          result.resynch();
        }
      });
    } else {
      myModifiable.addAWTChangeListener(life, new ChangeListener() {
        public void onChange() {
          result.resynch();
        }
      });
    }
    return result;
  }

  @Nullable
  private Condition<ResolvedItem> createNarrowingFilter(DBAttribute<Long> attribute, final ScalarModel<Set<ResolvedItem>> products,
    ItemHypercube cube)
  {
    if (Bug.attrProduct.equals(attribute)) {
      return new Condition<ResolvedItem>() {
        final long connection = myPrivateMetadata.thisConnectionItem();
        public boolean isAccepted(ResolvedItem value) {
          if (value == null)
            return false;
          if (connection != value.getConnectionItem())
            return false;
          Set<ResolvedItem> productSet = products.getValue();
          return productSet != null && productSet.contains(value);
        }
      };
    } else if (Bug.attrComponent.equals(attribute)) {
      return createDependentFilter(cube, products, SingleEnumAttribute.COMPONENT);
    } else if (Bug.attrVersion.equals(attribute)) {
      return createDependentFilter(cube, products, SingleEnumAttribute.VERSION);
    } else if (Bug.attrTargetMilestone.equals(attribute)) {
      return createDependentFilter(cube, products, SingleEnumAttribute.TARGET_MILESTONE);
    } else {
      return null;
    }
  }

  private Condition<ResolvedItem> createDependentFilter(final ItemHypercube cube,
    final ScalarModel<Set<ResolvedItem>> products, final SingleEnumAttribute enumAttr)
  {
    assert cube != null;
    return new Condition<ResolvedItem>() {
      final long connection = myPrivateMetadata.thisConnectionItem();
      public boolean isAccepted(ResolvedItem value) {
        if (value == null)
          return false;
        if (connection != value.getConnectionItem())
          return false;
        long component = value.getResolvedItem();
        if (!cube.allows(enumAttr.getBugAttribute(), component))
          return false;
        Set<ResolvedItem> productSet = products.getValue();
        if (productSet == null)
          return false;
        for (ResolvedItem product : productSet) {
          ProductDependencyInfo info = myProductDependencyMap.get(product);
          if (info != null && info.contains(enumAttr.getBugzillaAttribute(), component))
            return true;
        }
        return false;
      }
    };
  }

  private ScalarModel<Set<ResolvedItem>> createProductsModel(Lifespan life, final ItemHypercube cube) {
    final BasicScalarModel<Set<ResolvedItem>> model = BasicScalarModel.create(true, false);
    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        Set<ResolvedItem> newSet = getProductsSubset(cube);
        Set<ResolvedItem> oldSet = model.getValue();
        if (!Util.equals(oldSet, newSet)) {
          model.setValue(newSet);
        }
      }
    };
    listener.onChange();
    myModifiable.addAWTChangeListener(life, listener);
    return model;
  }

  private synchronized Set<ResolvedItem> getProductsSubset(ItemHypercube cube) {
    Set<ResolvedItem> newSet = Collections15.hashSet(myProductDependencyMap.keySet());
    for (Iterator<ResolvedItem> ii = newSet.iterator(); ii.hasNext();) {
      ResolvedItem product = ii.next();
      if (!acceptProduct(cube, product))
        ii.remove();
    }
    return newSet;
  }

  private boolean acceptProduct(ItemHypercube cube, ResolvedItem product) {
    if (product == null || cube == null)
      return false;
    if (!cube.allows(Bug.attrProduct, product.getResolvedItem()))
      return false;
    ProductDependencyInfo info = myProductDependencyMap.get(product);
    if (info == null)
      return false;
    if (!checkProduct(info, cube, SingleEnumAttribute.COMPONENT))
      return false;
    if (!checkProduct(info, cube, SingleEnumAttribute.VERSION))
      return false;
    if (!checkProduct(info, cube, SingleEnumAttribute.TARGET_MILESTONE))
      return false;
    return true;
  }

  private boolean checkProduct(ProductDependencyInfo info, ItemHypercube cube, SingleEnumAttribute enumAttr)
  {
    // todo analyze excludes also
    SortedSet<Long> values = cube.getIncludedValues(enumAttr.getBugAttribute());
    if (values == null)
      return true;
    return info.containsAny(enumAttr.getBugzillaAttribute(), values);
  }

  public synchronized ProductDependencyInfo getInfo(ItemKey selection) {
    if (selection == null)
      return null;
    return myProductDependencyMap.get(selection);
  }
}
