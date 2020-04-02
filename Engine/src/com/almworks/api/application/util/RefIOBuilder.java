package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.commons.Factory;
import com.almworks.util.properties.PropertyMap;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

/**
 * @author dyoma
 */
public class RefIOBuilder implements Factory<BaseModelKey.DataIO<ItemKey>> {
  private BaseModelKey.DataIO<ItemKey> myIO = null;
  private final DBAttribute<Long> myAttribute;
  private final ResolvedFactory myResolvedFactory;
  private ValueCreator myCreator;
  @Nullable
  private StateIconFactory myIconFactory;
  private boolean myIgnoreUserChanges;

  public RefIOBuilder(DBAttribute<Long> attribute, ResolvedFactory resolvedFactory) {
    assert attribute != null;
    assert resolvedFactory != null;
    myAttribute = attribute;
    myResolvedFactory = resolvedFactory;
  }

  public BaseModelKey.DataIO<ItemKey> create() {
    if (myIO == null) {
      assert myCreator != null;
      myIO = new SimpleRefIO(myAttribute, myResolvedFactory, myIconFactory, myCreator, myIgnoreUserChanges);
    }
    return myIO;
  }

  public void setIconFactory(StateIconFactory iconFactory) {
    assert myIO == null;
    myIconFactory = iconFactory;
  }

  public void setConstraintType(EnumConstraintType constraintType) {
    setValueCreator(new FindExisting(constraintType));
  }

  public void setValueCreator(ValueCreator creator) {
    assert myIO == null;
    myCreator = creator;
  }

  public void setIgnoreUserChanges(boolean value) {
    assert myIO == null;
    myIgnoreUserChanges = value;
  }

  private static class SimpleRefIO extends BaseSimpleIO<ItemKey, Long> {
    private final ResolvedFactory myFactory;
    private final StateIconFactory myStateIconFactory;
    private final ValueCreator myCreator;
    private final boolean myIgnoreUserChanges;

    protected SimpleRefIO(DBAttribute<Long> attribute, ResolvedFactory factory, StateIconFactory stateIconFactory,
      ValueCreator creator, boolean ignoreUserChanges)
    {
      super(attribute);
      myStateIconFactory = stateIconFactory;
      myFactory = factory;
      myCreator = creator;
      myIgnoreUserChanges = ignoreUserChanges;
    }

    @Nullable
    protected ResolvedItem extractValue(Long dbValue, ItemVersion version, LoadedItemServices itemServices) {
      try {
        return dbValue == null ? null : itemServices.getItemKeyCache().getItemKey(dbValue, version.getReader(), myFactory);
      } catch (BadItemException e) {
        return null;
      }
    }

    protected void setValue(ItemKey domValue, PropertyMap values, ModelKey<ItemKey> key) {
      super.setValue(domValue, values, key);
      if (myStateIconFactory != null) {
        if (domValue != null) {
          Icon icon = domValue.getIcon();
          if (icon != null) {
            StateIcon stateIcon = StateIconHelper.getCachedIcon(icon);
            if (stateIcon == null) {
              stateIcon = myStateIconFactory.createIcon(icon, getAttributeDisplayableName(), domValue.getDisplayName());
              StateIconHelper.putCachedIcon(icon, stateIcon);
            }
            StateIconHelper.addStateIcon(values, stateIcon);
          }
        }
      }
    }

    protected Long toDatabaseValue(UserChanges changes, ItemKey userInput) {
      String id = userInput == null ? null : userInput.getId();
      return myCreator.createValue(changes, id);
    }

    public void addChanges(UserChanges changes, ModelKey<ItemKey> modelKey) {
      if (!myIgnoreUserChanges) {
        super.addChanges(changes, modelKey);
      }
    }
  }


  public interface ValueCreator {
    Long createValue(UserChanges changes, @Nullable String input);
  }


  public static class FindExisting implements ValueCreator {
    private final EnumConstraintType myConstraintType;

    public FindExisting(EnumConstraintType constraintType) {
      myConstraintType = constraintType;
    }

    public Long createValue(UserChanges changes, @Nullable String input) {
      List<ResolvedItem> resolved = myConstraintType.resolveKey(input, changes.createConnectionCube());
      if (resolved.size() != 1)
        return getNotExisting(changes, input, resolved);
      return resolved.get(0).getResolvedItem();
    }

    protected Long getNotExisting(UserChanges changes, @Nullable String input, List<ResolvedItem> resolved) {
      assert resolved.size() == 0 : resolved;
      return null;
    }
  }
}
