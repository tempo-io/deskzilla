package com.almworks.api.application.util;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.ModelMergePolicy;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.collections.*;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.detach.Lifespan;

import javax.swing.text.PlainDocument;
import java.util.Comparator;
import java.util.Date;

/**
 * @author : Dyoma
 */
public class ValueKey<T> extends SingleValueModelKey<T, T> {
  private final Comparator<T> myComparator;

  public ValueKey(DBAttribute<T> attribute, Comparator<T> comparator, boolean exportAsString, String displayableName) {
    super(attribute, exportAsString, false, displayableName);
    myComparator = comparator;
  }

  public T getValue(ModelMap model) {
    return findSingleValue(model);
  }

  public void copyValue(ModelMap to, PropertyMap from) {
    setupSingleValue(to, getValue(from));
    register(to);
    to.valueChanged(this);
  }

  public int compare(T o1, T o2) {
    return Comparing.compare(myComparator, o1, o2);
  }

  public static <T> ValueKey<T> createValue(DBAttribute<T> attribute, Comparator<T> comparator, boolean exportAsString, String displayableName)
  {
    return new ValueKey<T>(attribute, comparator, exportAsString, displayableName);
  }

  public static <T> ValueKey<T> createSimpleValue(DBAttribute<T> attribute, Comparator<T> comparator, boolean exportAsString, String displayableName)
  {
    return createValue(attribute, comparator, exportAsString, displayableName);
  }

  public static <T extends Comparable> ValueKey<T> createComparableValue(DBAttribute<T> attribute, boolean exportAsString, String displayableName) {
    return createSimpleValue(attribute, Containers.<T>comparablesComparator(), exportAsString, displayableName);
  }

  public static ValueKey<Date> createDateValue(DBAttribute<Date> attribute, boolean exportAsString, String displayableName, final ModelMergePolicy mergePolicy) {
    return new DateValueKey(attribute, Containers.<Date>comparablesComparator(), exportAsString, displayableName, mergePolicy);
  }

  public <SM> SM getModel(final Lifespan lifespan, final ModelMap model, Class<SM> aClass) {
    if (!(aClass.isAssignableFrom(PlainDocument.class)))
      return super.getModel(lifespan, model, aClass);
    final PlainDocument document = new PlainDocument();
    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        if (!lifespan.isEnded()) {
          T value = getValue(model);
          DocumentUtil.setDocumentText(document, value != null ? value.toString() : "");
        }
      }
    };
    model.addAWTChangeListener(lifespan, listener);
    listener.onChange();
    return (SM) document;
  }
}
