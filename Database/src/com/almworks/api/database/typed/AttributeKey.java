package com.almworks.api.database.typed;

import org.almworks.util.TypedKey;
import org.almworks.util.TypedKeyRegistry;
import org.jetbrains.annotations.*;

public class AttributeKey<T> extends TypedKey<Attribute> {
  private final Class<T> myAttributeClass;

  private AttributeKey(String name, @NotNull Class<T> attributeClass,
    @NotNull TypedKeyRegistry<TypedKey<Attribute>> registry)
  {
    super(name, Attribute.class, registry);
    myAttributeClass = attributeClass;
  }

  public Class<T> getAttributeClass() {
    return myAttributeClass;
  }

  public static <T> AttributeKey<T> create(String name, Class<T> valueClass,
    @NotNull TypedKeyRegistry<TypedKey<Attribute>> registry)
  {
    return new AttributeKey<T>(name, valueClass, registry);
  }
}
