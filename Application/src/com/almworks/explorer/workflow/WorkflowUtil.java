package com.almworks.explorer.workflow;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelKey;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.util.advmodel.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

/**
 * @author Alex
 */

public class WorkflowUtil {
  public static final String DONT_CHANGE = "-- Don't Change --";

  public static AListModel<ItemKey> createEnumModel(Lifespan lifespan, ScalarModel<ItemHypercube> sourceCube,
    final BaseEnumConstraintDescriptor descriptor, @Nullable final Condition<ItemKey> filter)
  {
    final ListModelHolder<ItemKey> variantsHolder = ListModelHolder.create();
    final Lifecycle cubeLife = new Lifecycle();
    sourceCube.getEventSource().addListener(lifespan, ThreadGate.AWT, new ScalarModel.Adapter<ItemHypercube>() {
      public void onScalarChanged(ScalarModelEvent<ItemHypercube> event) {
        cubeLife.cycle();
        ItemHypercube cube = event.getNewValue();
        AListModel<ItemKey> variants =
          UIControllerUtil.getArtifactListModel(cubeLife.lifespan(), cube, descriptor, false);
        if (filter != null)
          variants = FilteringListDecorator.create(cubeLife.lifespan(), variants, filter);
        variantsHolder.setModel(variants);
      }
    });
    lifespan.add(cubeLife.getDisposeDetach());
    return variantsHolder;
  }

  public static String getMustContainComplaint(ModelKey<?> modelKey, Collection<? extends ItemKey> artifactList) {
    StringBuilder b = new StringBuilder().append(modelKey.getDisplayableName());
    String prefix = " must contain ";
    int len = b.length();
    for (ItemKey ra : artifactList) {
      b.append(prefix);
      if (b.length() >= 40) {
        b.setLength(len);
        b.append(" must contain a valid value");
        break;
      }
      b.append(ra.getDisplayName());
      prefix = " or ";
    }
    return b.toString();
  }

  public static String getMustNotContainComplaint(ModelKey<?> modelKey, ItemKey item) {
    return modelKey.getDisplayableName() + " must not contain " + item.getDisplayName();
  }

  public static String getEnumSaveProblem(ModelKey<?> modelKey, ItemKey item,
    Collection<? extends ItemKey> included, Collection<? extends ItemKey> excluded)
  {
    if (excluded != null && excluded.contains(item))
      return getMustNotContainComplaint(modelKey, item);
    if (included != null && !included.contains(item))
      return getMustContainComplaint(modelKey, included);
    if (item == null || item == ItemKey.INVALID) {
      return "Please select " + modelKey.getDisplayableName();
    }
    return null;
  }

  public static String getEnumSetSaveProblem(ModelKey<?> modelKey, List<?> items, List<? extends ItemKey> included,
    List<? extends ItemKey> excluded)
  {
    boolean includeOk = included == null;
    for (Object item : items) {
      if (item != null && !(item instanceof ItemKey)) {
        Log.warn("invalid item " + item);
        return null;
      }
      if (!includeOk && included.contains(item)) {
        includeOk = true;
      }
      if (excluded != null && excluded.contains(item)) {
        return getMustNotContainComplaint(modelKey, (ItemKey) item);
      }
    }
    if (!includeOk) {
      return getMustContainComplaint(modelKey, included);
    }
    return null;
  }
}
