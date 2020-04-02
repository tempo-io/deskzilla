package com.almworks.api.edit;

import com.almworks.api.application.UiItem;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.*;
import com.almworks.util.i18n.Local;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.Collections;

public final class ItemSyncSupport {
  private ItemSyncSupport() {}

  /**
   * Either throws CantPerformException or returns non-null EditControl.
   * If items are currently locked, brings to front their editor and throws CantPerformException.
   */
  @NotNull
  public static EditControl prepareEditOrShowLockingEditor(SyncManager syncMan, LongList items) throws CantPerformException {
    EditorLock lockingEditor = null;
    for (LongListIterator i = items.iterator(); i.hasNext();) {
      EditorLock lock = syncMan.findLock(i.next());
      if (lockingEditor != null && lock != lockingEditor) {
        throw new CantPerformExceptionExplained(L.content(Local.parse("Some " + Terms.ref_artifacts + " are already being edited")));
      }
      lockingEditor = lock;
    }
    if (lockingEditor != null) {
      lockingEditor.activateEditor();
      throw new CantPerformExceptionSilently("focused another editor");
    }
    EditControl editControl = syncMan.prepareEdit(items);
    return CantPerformException.ensureNotNull(editControl);
  }

  @NotNull
  public static EditControl prepareEditOrShowLockingEditor(ActionContext context, UiItem uiItem)
    throws CantPerformException
  {
    return prepareEditOrShowLockingEditor(context, Collections.singleton(uiItem));
  }

  @NotNull
  public static EditControl prepareEditOrShowLockingEditor(ActionContext context, Collection<? extends UiItem> uiItems)
    throws CantPerformException
  {
    LongList items = LongSet.collect(UiItem.GET_ITEM, uiItems);
    SyncManager manager = context.getSourceObject(SyncManager.ROLE);
    return prepareEditOrShowLockingEditor(manager, items);
  }

  public static <D> void startWindowedEdit(final EditControl editControl, final Function<DBReader, D> readData, final FunctionE<D, BasicWindowBuilder, CantPerformException> createWindow) throws CantPerformException {
    CantPerformException.ensureNotNull(editControl).start(new EditorFactory() {
      @Override
      public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) {
        final D data = readData.invoke(reader);
        return new WindowItemEditor(editControl, new FactoryE<BasicWindowBuilder, CantPerformException>() {
          @Override
          public BasicWindowBuilder create() throws CantPerformException {
            return createWindow.invoke(data);
          }
        });
      }
    });
  }
}
