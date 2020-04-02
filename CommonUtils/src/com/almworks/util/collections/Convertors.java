package com.almworks.util.collections;

import com.almworks.util.commons.Factory;
import org.almworks.util.Util;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

/**
 * @author : Dyoma
 */
public class Convertors {
  public static final Convertor<Object, String> TO_STRING = new Convertor<Object, String>() {
    public String convert(Object t) {
      return t == null ? null : t.toString();
    }
  };

  private static final Convertor ID_CONVERTOR = new Convertor() {
    public Object convert(Object value) {
      return value;
    }
  };

  public static final Convertor<String, String> TO_LOWER_CASE = new Convertor<String, String>() {
    public String convert(String value) {
      //noinspection ConstantConditions
      return value == null ? null : Util.lower(value);
    }
  };

  private static final Convertor<String, BigDecimal> PARSE_BIGDECIMAL = new Convertor<String, BigDecimal>() {
    @SuppressWarnings({"ConstantConditions"})
    public BigDecimal convert(String value) {
      try {
        return value != null ? new BigDecimal(value) : null;
      } catch (Exception e) {
        return null;
      }
    }
  };

  public static final Convertor<?, ?> TO_NULL = new Convertor<Object, Object>() {
    @Override
    public Object convert(Object value) {
      return null;
    }
  };

  public static <T> Convertor<T, String> getToString() {
    return (Convertor<T, String>) TO_STRING;
  }

  public static <D, R> Convertor<D, R> fromFactory(final Factory<R> factory) {
    return new Convertor<D, R>() {
      public R convert(D value) {
        return factory.create();
      }
    };
  }

  public static <D, R> Convertor<D, R> fromMap(final Map<D, R> map) {
    return new Convertor<D, R>() {
      public R convert(D key) {
        return map.get(key);
      }
    };
  }

  public static <T> Convertor<T, T> identity() {
    return ID_CONVERTOR;
  }

  public static <D, R> Convertor<D, R> constant(final R value) {
    return new Convertor<D, R>() {
      public R convert(D x) {
        return value;
      }
    };
  }

  public static Convertor<String, Date> dateParser(final DateFormat dateFormat) {
    return new Convertor<String, Date>() {
      public Date convert(String value) {
        try {
          return value != null ? dateFormat.parse(value) : null;
        } catch (ParseException e) {
          return null;
        }
      }
    };
  }

  public static Convertor<String, BigDecimal> parseBigDecimal() {
    return PARSE_BIGDECIMAL;
  }

  public static <D, R> Convertor<D, R> toNull() {
    return (Convertor<D, R>) TO_NULL;
  }

  public static final Convertor<String, String> replaceAll(final String regex, final String replacement) {
    return new Convertor<String, String>() {
      @Override
      public String convert(String value) {
        return value.replaceAll(regex, replacement);
      }
    };
  }

}
