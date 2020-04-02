package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelKey;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.bugzilla.provider.datalink.schema.custom.LoadedField;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.RepeatingListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;

import java.util.Collection;
import java.util.List;


public abstract class Select<T, S> extends BugzillaCustomField<T, S> {
  private final BoolExpr<DP> myOptionsFilter;
  private final CommonMetadata myMD;
  private BaseEnumConstraintDescriptor myDescriptor;

  public Select(DBAttribute<S> attribute, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, BoolExpr<DP> optionsFilter, ModelKey<T> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, fields, order, modelKey);
    assert attribute.getScalarClass() == Long.class;
    myOptionsFilter = optionsFilter;
    //noinspection ConstantConditions
    myMD = fields.getCommonMetadata();
  }

  public boolean isTextSearchEnabled() {
    return false;
  }

  protected ConstraintDescriptor createDescriptor() {
    myDescriptor = CustomField.createDescriptor(life(), getAttribute(), getDisplayName(), myOptionsFilter, myMD);
    return myDescriptor;
  }

  protected EnumConstraintType getEnumConstraintType() {
    if (myDescriptor == null) {
      createDescriptor();
    }
    return myDescriptor;
  }


  public Order createOrder() {
    return null;
  }

  protected AListModel<ItemKey> getVariantsModel(ConnectContext context) {
    EnumConstraintType descriptor = getEnumConstraintType();
    if (descriptor == null) return AListModel.EMPTY;
    AListModel<ItemKey> variants = descriptor.getEnumFullModel();
    LoadedField p = getOptionVisibilityField();
    if (p != null) {
      String controllerFieldName = p.getId();
      DBAttribute controllerAttr = p.getAttribute();
      long controllerFieldItem = p.getFieldItem();
      List<ModelKey<?>> customFields = BugzillaKeys.customFields.getValue(context.getModel());
      if (customFields != null) {
        ModelKey<?> controllerKey = BugzillaCustomField.findModelKeyByControllerName(controllerFieldName, customFields);
        EnumConstraintType controllerDescriptor = findEnumDescriptorByControllerName(controllerAttr, controllerFieldName, controllerKey);
        if (controllerKey != null && controllerDescriptor != null) {
          ControlledFieldListener listener =
            new ControlledFieldListener(controllerKey, controllerAttr, controllerFieldItem, context, descriptor, controllerDescriptor);
          listener.onChange();
          context.getModel().addAWTChangeListener(context.getLife(), listener);
          variants = listener.getModel();
        }
      }
    }
    return variants;
  }

  private EnumConstraintType findEnumDescriptorByControllerName(DBAttribute<?> field, String fieldName, ModelKey<?> controllerKey) {
    if (fieldName == null)
      return null;
    if (fieldName.startsWith("cf_")) {
      BugzillaCustomField<?, ?> bcf = BugzillaCustomField.fromModelKey(controllerKey);
      if (bcf != null) {
        ConstraintDescriptor descriptor = bcf.getDescriptor();
        if (descriptor instanceof EnumConstraintType)
          return (EnumConstraintType) descriptor;
      }
    } else {
      return CommonMetadata.getEnumDescriptor(field);
    }
    return null;
  }

  private static class ControlledFieldListener implements ChangeListener {
    private final Lifecycle myVariantsLife = new Lifecycle();
    private final RepeatingListModel<ItemKey> myModel = RepeatingListModel.create();
    private Object myLastControllerValue = null;
    private final ModelKey<?> myControllerKey;
    private final DBAttribute<?> myControllerField;
    private final long myControllerFieldItem;
    private final ConnectContext myContext;
    private final EnumConstraintType myDescriptor;
    private final EnumConstraintType myControllerDescriptor;

    private volatile boolean myGuard;

    public ControlledFieldListener(ModelKey<?> controllerKey, DBAttribute<?> controllerField, long controllerFieldItem,
      ConnectContext context, EnumConstraintType descriptor, EnumConstraintType controllerDescriptor)
    {
      myControllerKey = controllerKey;
      myControllerField = controllerField;
      myControllerFieldItem = controllerFieldItem;
      myContext = context;
      myDescriptor = descriptor;
      myControllerDescriptor = controllerDescriptor;
      context.getLife().add(myVariantsLife.getDisposeDetach());
    }

    public void onChange() {
      Threads.assertAWTThread();
      if (myGuard)
        return;
      myGuard = true;
      try {
        Object controllerValue = myControllerKey.getValue(myContext.getModel());
        if (Util.equals(controllerValue, myLastControllerValue) && myModel.isSourceSet())
          return;
        myLastControllerValue = controllerValue;
        ItemHypercubeImpl cube = (ItemHypercubeImpl) myContext.getDefaultCube();
        adjustCubeForControllerValue(cube, controllerValue);
        myVariantsLife.cycle();
        AListModel<ItemKey> newModel = myDescriptor.getEnumModel(myVariantsLife.lifespan(), cube);
        myModel.setSource(newModel);
      } finally {
        myGuard = false;
      }
    }

    private void adjustCubeForControllerValue(ItemHypercubeImpl cube, Object controllerValue) {
      List<Long> r = null;
      if (controllerValue instanceof ItemKey) {
        r = myControllerDescriptor.resolveItem(((ItemKey) controllerValue).getId(), cube);
      } else if (controllerValue instanceof Collection) {
        r = Collections15.arrayList();
        for (Object o : (Collection) controllerValue) {
          if (o instanceof ItemKey) {
            r.addAll(myControllerDescriptor.resolveItem(((ItemKey) o).getId(), cube));
          }
        }
      }
      if (r == null || r.isEmpty()) {
        // field will never be equal to itself: this way we ensure all dependent fields see
        // some non-existing value, which will make values filter
        cube.addValue(myControllerField, myControllerFieldItem, true);
      } else {
        cube.addValues(myControllerField, r, true);
      }
    }

    public AListModel<ItemKey> getModel() {
      return myModel.getModel();
    }
  }
}
