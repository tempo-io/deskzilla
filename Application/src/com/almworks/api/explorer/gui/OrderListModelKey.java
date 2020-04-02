package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.collections.*;
import com.almworks.util.components.*;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.*;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import javax.swing.event.DocumentEvent;
import javax.swing.text.PlainDocument;
import java.util.*;

/**
 * @author : Dyoma
 */
public abstract class OrderListModelKey <T extends Comparable, S extends Collection<Long>> extends AttributeModelKey<Collection<T>, S> {
  private final TypedKey<OrderListModel<T>> myModelKey;
  private CanvasRenderer<PropertyMap> myCanvasRenderer;

  public OrderListModelKey(DBAttribute<S> attribute, String displayableName) {
    super(attribute, displayableName);
    myModelKey = TypedKey.create(getName() + "#model");
  }

  @Override
  public Collection<T> getValue(ModelMap model) {
    return model.get(getValueKey());
  }

  @Override
  public boolean hasValue(ModelMap model) {
    Collection<T> collection = model.get(getValueKey());
    return collection != null;
  }

  @Override
  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    List<T> modelList = Collections15.arrayList(getValue(models));
    List<T> valuesList = Collections15.arrayList(getValue(values));
    Collections.sort(modelList);
    Collections.sort(valuesList);
    return Comparing.areOrdersEqual(modelList, valuesList);//hack about #562
  }

  @Override
  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return Util.equals(getValue(values1), getValue(values2));
  }

  @Override
  public void copyValue(final ModelMap to, PropertyMap from) {
    if (to == null) {
      assert false : this;
      return;
    }
    if (from == null) {
      assert false : this;
      return;
    }
    OrderListModel<T> m = to.get(myModelKey);
    if (m == null) {
      m = OrderListModel.create();
      to.put(myModelKey, m);
      to.registerKey(getName(), this);
    } else if (isEqualValue(to, from)) {
      return;
    }
    Collection<T> value = getValue(from);
    assert value != null : getAttribute();
    m.replaceElementsSet(value);
    to.put(getValueKey(), value);
    to.valueChanged(this);
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    S items = itemVersion.getValue(getAttribute());
    List<T> result = items == null ? Collections15.<T>emptyList() : extractValuesFrom(items, itemVersion, itemServices, values);
    setValue(values, result);
  }

  protected List<T> extractValuesFrom(S items, ItemVersion primaryItem, LoadedItemServices itemServices, PropertyMap values) {
    final List<T> result = Collections15.arrayList();
    for(final Long item : items) {
      final T key = extractValueFrom(item, primaryItem, itemServices);
      if(key != null) {
        result.add(key);
      }
    }
    return result;
  }

  @Override
  public void takeSnapshot(PropertyMap to, ModelMap from) {
    setValue(to, getValue(from));
  }

  @Nullable
  protected abstract T extractValueFrom(Long item, ItemVersion primaryItem, LoadedItemServices itemServices);

  @Override
  public int compare(Collection<T> o1, Collection<T> o2) {
    return 0;
  }

//  protected OrderListModel<T> getListModel(ModelMap model) {

//    return model.get(myModelKey);
//  }
  @Override
  public <SM> SM getModel(Lifespan lifespan, ModelMap modelMap, Class<SM> aClass) {
    final OrderListModel<T> model = modelMap.get(myModelKey);
    if (aClass.isInstance(model)) {
      lifespan.add(notifyModelMap(modelMap));
      return (SM) model;
    } else if (aClass.isAssignableFrom(PlainDocument.class)) {
      final PlainDocument document = new PlainDocument();
      DocumentUtil.addListener(lifespan, document, new DocumentAdapter() {
        @Override
        protected void documentChanged(DocumentEvent e) {
          TODO.notImplementedYet();
        }
      });
      ChangeListener listener = new ChangeListener() {
        @Override
        public void onChange() {
          DocumentUtil.setDocumentText(document, TextUtil.separate(model.toList(), ", ", Convertors.getToString()));
        }
      };
      model.addAWTChangeListener(lifespan, listener);
      return (SM) document;
    }
    throw TODO.notImplementedYet("Should be removed");
  }

  private Detach notifyModelMap(final ModelMap models) {
    DetachComposite life = new DetachComposite();
    models.get(myModelKey).addChangeListener(life, new ChangeListener() {
      @Override
      public void onChange() {
        models.valueChanged(OrderListModelKey.this);
      }
    });
    return  life;
  }

  @Override
  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    if (myCanvasRenderer == null)
      myCanvasRenderer = createRenderer();
    return myCanvasRenderer;
  }

  @NotNull
  protected CanvasRenderer<PropertyMap> createRenderer() {
    return new CanvasRenderer<PropertyMap>() {
      @Override
      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        Collection<T> value = getValue(item);
        String separator = "";
        if (value == null)
          return;
        for (T t : value) {
          canvas.appendText(separator);
          separator = ", ";
          if (t instanceof CanvasRenderable)
            ((CanvasRenderable) t).renderOn(canvas, state);
          else
            canvas.appendText(String.valueOf(t));
        }
      }
    };
  }

  public void replaceValues(ModelMap modelMap, List<T> keys) {
    Set<T> pending = Collections15.linkedHashSet(keys);
    Change change = change(modelMap);
    for (Iterator<T> ii = change.newValue().iterator(); ii.hasNext();) {
      T key = ii.next();
      if (!pending.remove(key)) {
        ii.remove();
      }
    }
    change.newValue().addAll(pending);
    change.done();
  }

//  public void addValue(ModelMap modelMap, T value) {
//    Change change = change(modelMap);
//    List<T> list = change.newValue();
//    if (list.indexOf(value) < 0)
//      list.add(value);
//    change.done();
//  }

  public void addValues(ModelMap modelMap, Collection<? extends T> values) {
    Change change = change(modelMap);
    List<T> list = change.newValue();
    for (T v : values)
      if (list.indexOf(v) < 0)
        list.add(v);
    change.done();
  }

//  public void removeValue(ModelMap modelMap, T key) {
//    Change change = change(modelMap);
//    change.newValue().remove(key);
//    change.done();
//  }

  protected Change change(ModelMap map) {
    return new Change(map);
  }

  protected class Change {
    private final PropertyMap myProps;
    private final List<T> myNewValue;
    private final ModelMap myMap;

    public Change(ModelMap map) {
      myMap = map;
      myProps = new PropertyMap();
      takeSnapshot(myProps, map);
      Collection<T> value = getValue(myProps);
      myNewValue = Collections15.arrayList(value);
    }

    public List<T> newValue() {
      return myNewValue;
    }

    public void done() {
      setValue(myProps, myNewValue);
      copyValue(myMap, myProps);
    }

    public <V> V getOtherValue(TypedKey<V> key) {
      return myProps.get(key);
    }

    public <V> void setOtherValue(TypedKey<V> key, V value) {
      myProps.put(key, value);
    }
  }
}
