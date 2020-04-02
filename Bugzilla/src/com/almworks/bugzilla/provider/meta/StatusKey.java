package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.AListModel;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

public class StatusKey extends ComboBoxModelKey {
  public StatusKey() {
    super(SingleEnumAttribute.STATUS, null);
  }

  @Override
  protected VariantsModelFactory extractVariants(ItemVersion itemVersion, LoadedItemServices itemServices) {
    VariantsModelFactory unfilteredFactory = super.extractVariants(itemVersion, itemServices);

    BugzillaContext context = BugzillaUtil.getContext(itemServices);
    if (context == null) return unfilteredFactory;

    String fromStatus = getFromStatus(itemVersion.getItem(), itemVersion.getReader());
    if (fromStatus == null) return unfilteredFactory;

    return new FilteringVariantsFactory(unfilteredFactory, context.getWorkflowTracker(), fromStatus);
  }

  @Nullable
  private String getFromStatus(long item, DBReader reader) {
    if (SyncUtils.isNew(item, reader) || Boolean.TRUE.equals(reader.getValue(item, SyncAttributes.IS_PROTOTYPE))) {
      // "pre-initial" status
      return "";
    } else {
      ItemVersion base = SyncUtils.readBaseIfExists(reader, item);
      ItemVersion trunk = SyncUtils.readTrunk(reader, item);
      // We take status from base if base is there to disallow subsequent changes of status: status is recorded as value, not as a history step, and only the latest value will be uploaded, which may be inapplicable.
      Long statusItem = base == null ? null : base.getValue(getAttribute());
      if (statusItem == null) statusItem = trunk.getValue(getAttribute());
      return statusItem == null ? null : getEnumAttr().getEnumType().getStringId(SyncUtils.readTrunk(reader, statusItem));
    }
  }

  private class FilteringVariantsFactory implements VariantsModelFactory {
    private final VariantsModelFactory myDelegate;
    private final WorkflowTracker myTracker;
    private final String myFromStatus;

    public FilteringVariantsFactory(VariantsModelFactory delegate, WorkflowTracker tracker, String fromStatus) {
      myDelegate = delegate;
      myTracker = tracker;
      myFromStatus = fromStatus;
    }

    public AListModel<ItemKey> createModel(Lifespan life) {
      if (life.isEnded())
        return AListModel.EMPTY;
      return myTracker.filterTargetModel(life, myDelegate.createModel(life), myFromStatus, true);
    }

    @NotNull
    @Override
    public List<ResolvedItem> getResolvedList() {
      return myTracker.filterTargetList(myDelegate.getResolvedList(), myFromStatus, true);
    }
  }
}
