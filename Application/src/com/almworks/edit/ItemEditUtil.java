package com.almworks.edit;

import com.almworks.api.application.*;
import com.almworks.api.gui.DialogManager;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.sync.*;
import com.almworks.items.util.AttributeMap;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.L;
import com.almworks.util.commons.Condition;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Log;

import java.util.*;

import static org.almworks.util.Collections15.arrayList;
import static org.almworks.util.Collections15.hashSet;

public class ItemEditUtil {
  public static Condition<ItemWrapper> available(final SyncManager sm) {
    return new Condition<ItemWrapper>() {
      @Override
      public boolean isAccepted(ItemWrapper value) {
        return value != null && sm.findLock(value.getItem()) == null;
      }
    };
  }

  /**
   * @param dialogMan used to show error message if commit fails
   */
  public static void addModelChanges(ItemVersionCreator creator, ItemUiModelImpl model, DialogManager dialogMan) throws DBOperationCancelledException {
    PropertyMap changes = model.getChanges();
    if (ModelKey.CHANGED_KEYS.getValue(changes).isEmpty())
      return;
    assert LoadedItemServices.VALUE_KEY.getValue(changes) == model.services();
    UserChangesImpl result = new UserChangesImpl(changes, creator);
    List<?> problems = result.getProblems();
    int problemCount = problems.size();

    List<ModelKey> list = arrayList(result.getNotReady());

    // first - all keys that are not coming last
    for (Iterator<ModelKey> ii = list.iterator(); ii.hasNext();) {
      ModelKey key = ii.next();
      if (!(key instanceof ModelKeyComingLast)) {
        key.addChanges(result);
        problemCount = warnAboutProblems(problems, problemCount, key);
        ii.remove();
      }
    }

    // then all other keys
    for (ModelKey key : list) {
      key.addChanges(result);
      problemCount = warnAboutProblems(problems, problemCount, key);
    }
    if (problemCount > 0) {
      showCommitProblems(problems, dialogMan);
      throw new DBOperationCancelledException();
    }
  }

  private static void showCommitProblems(List<?> commitProblems, DialogManager dialogMan) {
    String message;
    if (commitProblems.size() == 0) {
      message = "<html><body>Changes were not saved.<br><br>" +
        "This is most probably caused by a defect in the application.<br>" +
        "Please contact support team for assistance.<br>";
    } else {
      StringBuffer sb = new StringBuffer();
      for (Object problem : commitProblems) {
        if (problem != null) {
          sb.append(String.valueOf(problem)).append(" \n");
        }
      }
      message = "<html><body>Changes were not saved due to the following errors:<br><br>" + sb;
    }
    dialogMan.showErrorMessage(L.dialog("Save Failed"), L.html(message));
  }

  private static int warnAboutProblems(List<?> problems, int problemCount, ModelKey<?> key) {
    int newProblemCount = problems.size();
    if (newProblemCount > problemCount) {
      for (int i = problemCount; i < newProblemCount; i++) {
        Object problem = problems.get(i);
        if (problem instanceof Throwable) {
          Log.warn("commit problem (" + key + "): " + problem, (Throwable) problem);
        } else {
          Log.warn("commit problem (" + key + "): " + problem);
        }
      }
      problemCount = newProblemCount;
    }
    return problemCount;
  }

  public static ItemVersionCreator copyPrototype(EditDrain drain, long prototypeItem) {
    ItemVersionCreator creator = drain.createItem();
    ItemVersion prototype = creator.forItem(prototypeItem);
    AttributeMap values = prototype.getAllValues();

    // do not copy some values
    final Set<DBAttribute<?>> attributesToCopy = hashSet(values.keySet());
    // will expunge real prototype if we copy ItemStorage's ID
    attributesToCopy.remove(DBAttribute.ID);
    // we're not creating another prototype
    attributesToCopy.remove(SyncAttributes.IS_PROTOTYPE);
    attributesToCopy.remove(DBAttribute.NAME);
    for (DBAttribute a : attributesToCopy) {
      creator.setValue(a, values.get(a));
    }
    creator.setValue(SyncAttributes.ITEM_DOWNLOAD_STAGE, ItemDownloadStage.NEW.getDbValue());
    return creator;
    // todo set modification and creation time
    // note: this todo was taken from old AbstractConnection.copyPrototype. Currently all values should be set into prototype by the relevant connection.
  }
}
