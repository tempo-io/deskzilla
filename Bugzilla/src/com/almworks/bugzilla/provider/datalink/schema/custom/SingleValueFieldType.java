package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.integration.data.CustomFieldType;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Convertors;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.util.Date;
import java.util.List;

abstract class SingleValueFieldType<T> extends FieldType {
  SingleValueFieldType(Class<T> scalarClass, String typeId, CustomFieldType type) {
    super(scalarClass, DBAttribute.ScalarComposition.SCALAR, typeId, NS.object(typeId), type);
  }

  @Override
  public void updateField(ModifiableField field, List<String> options, boolean strictInfos) {
    ItemVersionCreator creator = field.getCreator();
    SyncUtils.deleteAll(creator, creator.getSlaves(CustomField.AT_OPT_FIELD));
  }

  @Override
  public boolean acceptsRawValue(@Nullable List<String> values) {
    return values == null || values.isEmpty() || values.size() == 1;
  }

  @Override
  public void setRawValue(ItemVersionCreator bug, DBAttribute<?> attribute, List<String> values) {
    if (values == null || values.isEmpty()) {
      bug.setValue(attribute, null);
      return;
    }
    if (values.size() > 1) Log.error("Expected single value " + attribute.getId() + " " + values);
    String val = values.get(0);
    if (val == null || val.trim().length() == 0) {
      bug.setValue(attribute, null);
      return;
    }
    T dbValue = convertString(bug, val);
    //noinspection unchecked
    bug.setValue((DBAttribute<T>) attribute, dbValue);
  }

  @Nullable
  protected abstract T convertString(ItemVersionCreator bug, String val);

  public static ScalarFieldType<String> text(String typeId, CustomFieldType type) {
    return new ScalarFieldType<String>(String.class, typeId, type,
      Convertors.<String>identity(), Convertors.<String, Boolean>constant(true));
  }

  private static class DateConvertor {
    private String myLastString;
    private Date myLastDate;

    public Convertor<String, Boolean> acceptor() {
      return new Convertor<String, Boolean>() {
        @Override
        public Boolean convert(String value) {
          if(value == null || value.trim().isEmpty()) {
            myLastString = value;
            myLastDate = null;
            return true;
          }
          myLastString = value;
          myLastDate = BugzillaDateUtil.DEFAULT_DATE_PARSER.convert(value);
          return myLastDate != null;
        }
      };
    }

    public Convertor<String, Date> convertor() {
      return new Convertor<String, Date>() {
        @Override
        public Date convert(String value) {
          if(!Util.equals(myLastString, value)) {
            myLastString = value;
            myLastDate = BugzillaDateUtil.DEFAULT_DATE_PARSER.convert(value);
          }
          return myLastDate;
        }
      };
    }
  }

  public static SingleValueFieldType<?> date(String typeId, CustomFieldType type) {
    final DateConvertor c = new DateConvertor();
    return new ScalarFieldType<Date>(Date.class, typeId, type, c.convertor(), c.acceptor());
  }

  private static class ScalarFieldType<T> extends SingleValueFieldType<T> {
    private final Convertor<String, Boolean> myAcceptValue;
    private final Convertor<String, T> myConvertValue;

    ScalarFieldType(
      Class<T> scalarClass, String typeId, CustomFieldType type,
      Convertor<String, T> convertValue, Convertor<String, Boolean> acceptValue)
    {
      super(scalarClass, typeId, type);
      myConvertValue = convertValue;
      myAcceptValue = acceptValue;
    }

    @Override
    public boolean acceptsRawValue(@Nullable List<String> values) {
      if(values == null || values.isEmpty()) {
        return true;
      }
      if(values.size() == 1) {
        return Boolean.TRUE.equals(myAcceptValue.convert(values.get(0)));
      }
      return false;
    }

    protected T convertString(ItemVersionCreator bug, String val) {
      return myConvertValue.convert(val);
    }
  }
}
