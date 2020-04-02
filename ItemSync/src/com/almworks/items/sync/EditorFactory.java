package com.almworks.items.sync;

import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import org.jetbrains.annotations.*;

public interface EditorFactory {
  /**
   * Collect data required for edit and create editor
   * @return prepared editor. Null means that edit has to be cancelled
   */
  @Nullable
  ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) throws DBOperationCancelledException;
}
