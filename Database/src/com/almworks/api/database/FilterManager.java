package com.almworks.api.database;

import com.almworks.api.database.typed.AttributeKey;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.Collection;

public interface FilterManager {
  @Nullable
  Filter or(@Nullable  Filter query1, @Nullable  Filter query2);

  @Nullable
  Filter and(@Nullable Filter query1, @Nullable Filter query2);

  @Nullable
  Filter minus(@Nullable Filter query1, @Nullable Filter query2);

  @Nullable
  Filter not(@Nullable Filter filter);

  Filter.Equals attributeEquals(ArtifactPointer attribute, Object value, boolean indexable);

  <T> Filter.Equals attributeEquals(AttributeKey<T> systemAttribute, Object value, boolean indexable);

  Filter attributeSet(ArtifactPointer attribute, boolean indexable);

  <T> Filter attributeSet(AttributeKey<T> systemAttribute, boolean indexable);

  Filter boundedInteger(ArtifactPointer intAttribute, BigDecimal bound, boolean isLower, boolean strict);

  Filter intervalInteger(ArtifactPointer intAttribute, BigDecimal lower, BigDecimal upper, boolean strict);

  Filter textContains(ArtifactPointer attribute, String fragment);

  Filter isLocalChange();

  /**
   * filter accepts revisions which have attribute of array type and where one of the values in the array
   * is equal to value
   */
  Filter containsReference(ArtifactPointer attribute, ArtifactPointer value, boolean indexable);

  Filter containsAtLeastOneReference(ArtifactPointer attribute, Collection<? extends ArtifactPointer> value, boolean indexable);
}

