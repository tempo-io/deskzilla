package com.almworks.api.application.util;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 * @param <V> "domain" value type - stored in PropertyMap and is accessible in Application
 * @param <S> "source" value type - stored in DB and is converted to V
 */
public abstract class BaseSimpleIO<V, S> implements BaseModelKey.DataIO<V> {
  private final DBAttribute<S> myAttribute;

  protected BaseSimpleIO(DBAttribute<S> attribute) {
    myAttribute = attribute;
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<V> key) {
    setValue(extractValue(itemVersion.getValue(myAttribute), itemVersion, itemServices), values, key);
  }

  protected String getAttributeName() {
    return getAttribute().getName();
  }

  protected String getAttributeDisplayableName() {
    return getAttribute().toString();
  }

  protected DBAttribute<S> getAttribute() {
    return myAttribute;
  }

  protected void setValue(V domValue, PropertyMap values, ModelKey<V> key) {
    key.setValue(values, domValue);
  }

  @Nullable
  protected abstract V extractValue(S dbValue, ItemVersion version, LoadedItemServices itemServices);

  @Nullable
  protected abstract S toDatabaseValue(UserChanges changes, V userInput);

  public void addChanges(UserChanges changes, ModelKey<V> key) {
    V userInput = changes.getNewValue(key);
    S value = toDatabaseValue(changes, userInput);
    changes.getCreator().setValue(myAttribute, value);
  }

  public <SM>SM getModel(Lifespan life, ModelMap model, ModelKey<V> key, Class<SM> aClass) {
    throw TODO.notImplementedYet(getClass().getName());
  }

  public static abstract class CollectionIO<E, SE, CE extends Collection<E>, CSE extends Collection<SE>>
    extends BaseSimpleIO<CE, CSE>
  {
    public CollectionIO(DBAttribute<CSE> attribute) {
      super(attribute);
    }

    @Override
    public <SM> SM getModel(Lifespan life, final ModelMap modelMap, final ModelKey<CE> key, Class<SM> aClass) {
      if (aClass.isAssignableFrom(AListModel.class)) {
        final OrderListModel<E> model = OrderListModel.create();
        ChangeListener listener = new ChangeListener() {
          public void onChange() {
            model.replaceElementsSet(getList(modelMap, key));
          }
        };
        modelMap.addAWTChangeListener(life, listener);
        listener.onChange();
        return (SM) model;
      } else {
        assert false : key;
        return null;
      }
    }

    protected CE getList(ModelMap model, ModelKey<CE> key) {
      final CE ce = key.getValue(model);
      if (ce == null)
        return emptyCollection();
      else
        return ce;
    }

    protected abstract CE emptyCollection();
  }

  public static abstract class ListIO<E, SE> extends CollectionIO<E, SE, List<E>, List<SE>> {
    public ListIO(DBAttribute<List<SE>> attribute) {
      super(attribute);
    }

    @Override
    protected List<E> emptyCollection() {
      return Collections15.emptyList();
    }
  }

  public static abstract class SetIO<E, SE> extends CollectionIO<E, SE, Set<E>, Set<SE>> {
    public SetIO(DBAttribute<Set<SE>> attribute) {
      super(attribute);
    }

    @Override
    protected Set<E> emptyCollection() {
      return Collections15.emptySet();
    }
  }
}