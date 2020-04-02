package com.almworks.bugzilla.provider;

import com.almworks.api.application.ItemKey;
import com.almworks.api.store.Store;
import com.almworks.bugzilla.integration.data.StatusInfo;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Condition;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import java.util.*;

public class WorkflowTracker {
  private final WorkflowScheme myWorkflow = new WorkflowScheme();
  private final Store myStore;
  private final SimpleModifiable myModifiable = new SimpleModifiable();

  public WorkflowTracker(Store store) {
    myStore = store;
  }

  /**
   * @return null means "don't know"
   */
  public Boolean isOpen(String status) {
    return myWorkflow.isOpen(status);
  }

  public void reportWorkflow(String currentStatus, List<StatusInfo> allowedStatusChanges,
    String markDuplicateStatus)
  {
    acceptChange(myWorkflow.reportWorkflow(currentStatus, allowedStatusChanges, markDuplicateStatus));
  }


  public void reportInitialStatuses(List<String> initialStatuses) {
    acceptChange(myWorkflow.reportInitialStatuses(initialStatuses));
  }

  public void reportStatusInfos(List<StatusInfo> statusInfos) {
    acceptChange(myWorkflow.reportStatusInfos(statusInfos));
  }

  public void clearState() {
    myWorkflow.clear();
    acceptChange(true);
  }

  private void acceptChange(boolean changed) {
    if (changed) {
      myModifiable.fireChanged();
      myWorkflow.saveTo(myStore);
    }
  }

  public void start() {
    myWorkflow.loadFrom(myStore);
  }

  public AListModel<ItemKey> filterTargetModel(Lifespan life, AListModel<ItemKey> model,
    final String fromStatus, final boolean includeSelf)
  {
    if (model == AListModel.EMPTY || fromStatus == null || model == null)
      return model;
    final FilteringListDecorator<ItemKey> result =
      FilteringListDecorator.create(life, model, new Condition<ItemKey>() {
        @Override
        public boolean isAccepted(ItemKey value) {
          Set<String> targetStati = myWorkflow.getAvailableTransitions(fromStatus);
          return isTargetAccepted(value, fromStatus, targetStati, includeSelf);
        }
      });
    myModifiable.addAWTChangeListener(life, new ChangeListener() {
      public void onChange() {
        result.resynch();
      }
    });
    return result;
  }

  /**
   * @param fromStatus empty string (not null) means "non-existent" status for new bugs
   */
  public <T extends ItemKey> List<T> filterTargetList(List<T> list, String fromStatus, boolean includeSelf) {
    if (fromStatus == null || list == null || list.isEmpty())
      return list;
    Set<String> targetStati = myWorkflow.getAvailableTransitions(fromStatus);
    if (targetStati == null)
      return list;
    List<T> r = Collections15.arrayList(list);
    for (Iterator<T> ii = r.iterator(); ii.hasNext();) {
      ItemKey artifactKey = ii.next();
      if (!isTargetAccepted(artifactKey, fromStatus, targetStati, includeSelf)) {
        ii.remove();
      }
    }
    return r;
  }

  private static boolean isTargetAccepted(ItemKey artifactKey, String fromStatus, Set<String> targetStati,
    boolean includeSelf)
  {
    if (targetStati == null || fromStatus == null)
      return true;
    String id = artifactKey.getId();
    boolean accept = targetStati.contains(id) || includeSelf && fromStatus.equals(id);
    return accept;
  }
}
