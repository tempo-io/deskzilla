package com.almworks.api.explorer.util;

import com.almworks.api.application.*;
import com.almworks.api.syncreg.*;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.*;
import org.almworks.util.detach.Lifespan;

/**
 * @author dyoma
 */
public class UIControllerUtil {
  public static final Convertor<ModelMap, ItemHypercube> DEFAULT_CUBE_CONVERTOR =
    new Convertor<ModelMap, ItemHypercube>() {
      public ItemHypercube convert(ModelMap modelMap) {
        LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(modelMap);
        ItemHypercubeImpl cube = new ItemHypercubeImpl();
        return ItemHypercubeUtils.adjustForConnection(cube, lis == null ? null : lis.getConnection());
      }
    };

  /**
   * @deprecated see {@link com.almworks.api.explorer.util.UIControllerUtil.ListModelGetter}
   */
  @Deprecated
  public static ListModelGetter createListModelGetter(
    final BaseEnumConstraintDescriptor descriptor, final Convertor<ModelMap, ItemHypercube> cubeGetter)
  {
    return new ListModelGetter(cubeGetter, descriptor);
  }

  public static <T extends ItemKey> AListModel<T> getArtifactListModel(Lifespan life,
    final ItemHypercube cube, final BaseEnumConstraintDescriptor descriptor, boolean includeMissing)
  {
    AListModel<T> result = descriptor.getResolvedEnumModel(life, cube);
    if (!includeMissing) {
      final ResolvedItem missing = descriptor.getMissingItem();
      result = FilteringListDecorator.create(life, result, new Condition<T>() {
        @Override
        public boolean isAccepted(T value) {
          return value != null && !value.equals(missing);
        }
      });
    }
    return result;
  }

  /**
   * @deprecated replaced with subclasses of {@link com.almworks.api.explorer.util.VariantsConfigurator}
   */
  @Deprecated
  public static class ListModelGetter implements Function<ConnectContext, AListModel<ItemKey>> {
    private final Convertor<ModelMap, ItemHypercube> myCubeGetter;
    private final BaseEnumConstraintDescriptor myDescriptor;
    private boolean myIncludeMissing = true;

    public ListModelGetter(Convertor<ModelMap, ItemHypercube> cubeGetter, BaseEnumConstraintDescriptor descriptor) {
      myCubeGetter = cubeGetter;
      myDescriptor = descriptor;
    }

    public AListModel<ItemKey> invoke(ConnectContext context) {
      final ItemHypercube cube = myCubeGetter.convert(context.getModel());
      Lifespan lifespan = context.getLife();
      return getArtifactListModel(lifespan, cube, myDescriptor, myIncludeMissing);
    }

    public void excludeMissing() {
      myIncludeMissing = false;
    }
  }

  public static boolean isEnabled(Object o) {
    if(o instanceof Enabled) {
      return ((Enabled)o).isEnabled();
    }
    return true;
  }

  public static final Condition<Object> IS_ENABLED = new Condition<Object>() {
    @Override
    public boolean isAccepted(Object value) {
      return isEnabled(value);
    }
  };
}
