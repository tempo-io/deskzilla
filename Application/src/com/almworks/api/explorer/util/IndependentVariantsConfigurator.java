package com.almworks.api.explorer.util;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.SortedListDecorator;
import com.almworks.util.collections.Convertor;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public class IndependentVariantsConfigurator implements VariantsConfigurator<ItemKey> {
  private final Convertor<ModelMap, ItemHypercube> myCubeGetter;
  private final BaseEnumConstraintDescriptor myDescriptor;
  private final Comparator<? super ItemKey> myOrder;
  private final boolean myIncludeMissing;

  public IndependentVariantsConfigurator(Convertor<ModelMap, ItemHypercube> cubeGetter,
    BaseEnumConstraintDescriptor descriptor, @Nullable Comparator<? super ItemKey> order)
  {
    this(cubeGetter, descriptor, order, true);
  }

  public IndependentVariantsConfigurator(Convertor<ModelMap, ItemHypercube> cubeGetter,
    BaseEnumConstraintDescriptor descriptor, @Nullable Comparator<? super ItemKey> order, boolean includeMissing)
  {
    myCubeGetter = cubeGetter;
    myDescriptor = descriptor;
    myOrder = order;
    myIncludeMissing = includeMissing;
  }

  public void configure(ConnectContext context, VariantsAcceptor<ItemKey> acceptor) {
    final ItemHypercube cube = myCubeGetter.convert(context.getModel());
    Lifespan lifespan = context.getLife();
    AListModel<ItemKey> variants =
      UIControllerUtil.getArtifactListModel(lifespan, cube, myDescriptor, myIncludeMissing);
    if (myOrder != null)
      variants = SortedListDecorator.create(context.getLife(), variants, myOrder);
    acceptor.accept(variants, context.getRecentConfig(myDescriptor.getId()));
  }

  public Convertor<ItemKey, String> getIdentityConvertor() {
    return ItemKey.GET_ID;
  }
}
