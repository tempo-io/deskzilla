package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.collections.Containers;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.*;

/**
 * @author dyoma
 */
public abstract class ItemsListKey extends OrderListModelKey<ItemKey, Set<Long>> {
  private final TextResolver myResolver;

  public ItemsListKey(DBAttribute<Set<Long>> attribute, TextResolver resolver, String displayableName) {
    super(attribute, displayableName);
    myResolver = resolver;
  }

  public TextResolver getResolver() {
    return myResolver;
  }

  public void addChanges(UserChanges changes) {
    changes.resolveArrayValue(this);
  }

  public ModelMergePolicy getMergePolicy() {
    return new ModelMergePolicy.AbstractPolicy() {
      public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
        mergeIntoModel(key, model, base, branch);
        return true;
      }

      public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
        Collection<ItemKey> removed = getValue(base);
        Collection<ItemKey> added = getValue(newLocal);

        Change change = change(model);
        List<ItemKey> newValue = change.newValue();
        Collection<ItemKey> newRetained = Containers.intersect(removed, added);
        added.removeAll(newRetained);
        removed.removeAll(newRetained);
        added.removeAll(newValue);
        newValue.addAll(added);
        newValue.removeAll(removed);
        change.done();
      }
    };
  }

  public <T> ModelOperation<T> getOperation(TypedKey<T> key) {
    if (ModelOperation.ADD_STRING_VALUE.equals(key))
      return (ModelOperation)new ModelOperation<Collection<String>>() {
        public void perform(ItemUiModel model, Collection<String> argument) throws CantPerformExceptionExplained {
          List<ItemKey> users = resolveUsers(argument);
          if (!users.isEmpty())
            addValues(model.getModelMap(), users);
        }

        public String getArgumentProblem(Collection<String> argument) {
          for (String userName : argument) {
            ItemKey user = resolveUser(userName);
            if (user == null)
              return "Wrong user name: '" + argument + "'";
          }
          return null;
        }

        private ItemKey resolveUser(String argument) {
          return getResolver().convert(argument);
        }

        private List<ItemKey> resolveUsers(Collection<String> argument) {
          List<ItemKey> list = Collections15.arrayList(argument.size());
          for (String name : argument) {
            ItemKey user = resolveUser(name);
            if (user != null)
              list.add(user);
          }
          return list;
        }
      };
    return super.getOperation(key);
  }
}
