package com.almworks.status;

import com.almworks.api.application.*;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.explorer.SimpleCollection;
import com.almworks.items.api.DBReader;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.model.SetHolder;
import com.almworks.util.ui.actions.*;

import java.util.Iterator;

import static com.almworks.util.collections.Functional.convert;
import static com.almworks.util.model.SetHolderUtils.fromChangeListener;

class SyncProblemItemsCounter extends StatComponent {
  private final Engine myEngine;
  private final Condition myCondition = new Condition() {
    public boolean isAccepted(Object o) {
      return ItemSyncProblem.class.isInstance(o) && ((ItemSyncProblem) o).isSerious();
    }
  };
  private static final TabKey SYNC_PROBLEMS_KEY = new ReplaceTabKey("SyncProblem");

  public SyncProblemItemsCounter(Engine engine) {
    super(Icons.ARTIFACT_STATE_HAS_SYNC_PROBLEM, ThreadGate.AWT);
    myEngine = engine;
  }

  protected String getTooltip(int count) {
    return L.tooltip(items(count) + " had problems during last synchronization");
  }

  public void attach() {
    SetHolder<SyncProblem> problems = myEngine.getSynchronizer().getProblems();
    problems.addInitListener(lifespan(), ThreadGate.AWT, fromChangeListener(new ChangeListener() {
      public void onChange() {
        recount();
      }
    }));
    addActionListener(new AnActionListener() {
      public void perform(ActionContext context) throws CantPerformException {
        showItems(context);
      }
    });
    super.attach();
  }

  private void showItems(ActionContext context) throws CantPerformException {
    final Engine engine = context.getSourceObject(Engine.ROLE);
    SimpleCollection source = new SimpleCollection() {
      @Override
      protected Iterator<Long> getItems() {
        return convert(ItemSyncProblem.SELECT.invoke(engine.getSynchronizer().getProblems().copyCurrent()), ItemSyncProblem.TO_ITEM).iterator();
      }
    };
    ExplorerComponent explorer = context.getSourceObject(ExplorerComponent.ROLE);
    explorer.showItemsInTab(source, ItemCollectionContext.createNoNode(
      L.content(Local.parse(Terms.ref_Artifacts + " with synchronization problems")), null, SYNC_PROBLEMS_KEY), false);
  }

  @Override
  protected boolean isCountNeedingDatabase() {
    return false;
  }

  @Override
  protected int count(DBReader reader) {
    return myCondition.count(myEngine.getSynchronizer().getProblems().copyCurrent());
  }

  public static SyncProblemItemsCounter create(ComponentContainer container) {
    return container.instantiate(SyncProblemItemsCounter.class);
  }
}
