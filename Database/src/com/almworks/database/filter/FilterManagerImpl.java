package com.almworks.database.filter;

import com.almworks.api.database.*;
import com.almworks.api.database.typed.AttributeKey;
import com.almworks.database.Basis;
import com.almworks.database.SystemObjectResolver;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.Collection;

import static com.almworks.api.database.Filter.ALL;
import static com.almworks.api.database.Filter.NONE;

public class FilterManagerImpl implements FilterManager {
  private final Basis myBasis;
  private final SystemObjectResolver myResolver;

  public FilterManagerImpl(Basis basis, SystemObjectResolver resolver) {
    assert resolver != null;
    assert basis != null;
    myBasis = basis;
    myResolver = resolver;
  }

  @Nullable
  public Filter or(@Nullable Filter query1, @Nullable Filter query2) {
    if (query1 == NONE || query1 == null || query2 == ALL) {
      return query2;
    } else if (query2 == NONE || query2 == null || query1 == ALL) {
      return query1;
    } else {
      return new Or(myBasis, query1, query2);
    }
  }

  @Nullable
  public Filter and(@Nullable Filter query1, @Nullable Filter query2) {
    if (query1 == NONE || query2 == ALL || query2 == null) {
      return query1;
    } else if (query2 == NONE || query1 == ALL || query1 == null) {
      return query2;
    }
    return new And(myBasis, query1, query2);
  }

  @Nullable
  public Filter minus(@Nullable Filter query1, @Nullable Filter query2) {
    if (query1 == NONE || query2 == NONE || query2 == null || query1 == null)
      return query1;
    if (query2 == ALL)
      return NONE;
    return new Minus(myBasis, query1, query2);
  }

  @Nullable
  public Filter not(@Nullable Filter filter) {
    if (filter == null) return null;
    if (filter == NONE) return ALL;
    if (filter == ALL) return NONE;
    return new Not(myBasis, filter);
  }

  public Filter.Equals attributeEquals(ArtifactPointer attribute, Object value, boolean indexable) {
    return new AttributeEquals(myBasis, attribute, value, indexable);
  }

  public <T> Filter.Equals attributeEquals(AttributeKey<T> systemAttribute, Object value, boolean indexable) {
    return attributeEquals(myResolver.getSystemObject(systemAttribute), value, indexable);
  }

  public Filter attributeSet(ArtifactPointer attribute, boolean indexable) {
    return new AttributeSet(myBasis, attribute, indexable);
  }

  public <T> Filter attributeSet(AttributeKey<T> systemAttribute, boolean indexable) {
    return attributeSet(myResolver.getSystemObject(systemAttribute), indexable);
  }

  public Filter boundedInteger(final ArtifactPointer intAttribute, final BigDecimal bound, final boolean isLower,
    boolean strict)
  {
    return new BoundedIntegerFilter(intAttribute, isLower, bound, strict);
  }

  public Filter intervalInteger(ArtifactPointer intAttribute, BigDecimal lower, BigDecimal upper, boolean strict) {
    int comp = lower.compareTo(upper);
    if (comp > 0 || (comp == 0 && strict))
      return NONE;
    return and(boundedInteger(intAttribute, lower, true, strict), boundedInteger(intAttribute, upper, false, strict));
  }

  public Filter textContains(final ArtifactPointer attribute, String fragment) {
    return new TextContainsFilter(attribute, fragment);
  }

  public Filter isLocalChange() {
    return new IsLocalChangeFilter();
  }

  public Filter containsReference(ArtifactPointer attribute, ArtifactPointer value, boolean indexable) {
    return new AttributeContainsReference(myBasis, attribute, value, indexable);
  }

  public Filter containsAtLeastOneReference(ArtifactPointer attribute, Collection<? extends ArtifactPointer> value,
    boolean indexable)
  {
    return new AttributeContainsAtLeastOneReference(myBasis, attribute, value, indexable);
  }

  private static class BoundedIntegerFilter implements Filter {
    private final ArtifactPointer myIntAttribute;
    private final boolean myLower;
    private final BigDecimal myBound;
    private final boolean myStrict;

    public BoundedIntegerFilter(ArtifactPointer intAttribute, boolean lower, BigDecimal bound, boolean strict) {
      myIntAttribute = intAttribute;
      myLower = lower;
      myBound = bound;
      myStrict = strict;
    }

    public boolean accept(Revision revision) {
      Value attributeValue = revision.getValue(myIntAttribute);
      if (attributeValue == null)
        return false;
      BigDecimal value = getValue(attributeValue);
      if (value == null)
        return false;
      int compare = value.compareTo(myBound);
      if (myStrict && compare == 0)
        return false;
      return myLower ? compare >= 0 : compare <= 0;
    }

    @Nullable
    private BigDecimal getValue(Value value) {
      Integer intValue = value.getValue(Integer.class);
      if (intValue != null)
        return BigDecimal.valueOf(intValue);
      BigDecimal decimal = value.getValue(BigDecimal.class);
      if (decimal != null)
        return decimal;
      Log.warn("not a decimal attribute " + myIntAttribute + " : " + value.toString() + ":" + value.getType());
      return null;
    }

    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      final BoundedIntegerFilter that = (BoundedIntegerFilter) o;

      if (!myStrict == that.myStrict)
        return false;
      if (!Util.equals(myBound, that.myBound))
        return false;
      if (myLower != that.myLower)
        return false;
      if (!myIntAttribute.equals(that.myIntAttribute))
        return false;
      return true;
    }

    public int hashCode() {
      int result;
      result = myIntAttribute.hashCode();
      result = 29 * result + (myLower ? 1 : 0);
      result = 29 * result + myBound.intValue();
      return result;
    }
  }


  private static final class IsLocalChangeFilter implements Filter {
    public boolean accept(Revision revision) {
      RCBArtifact rcb = revision.getArtifact().getRCBExtension(false);
      if (rcb == null)
        return false;
      boolean result = rcb.isLocalRevision(revision);
      return result;
    }

    public String toString() {
      return "isLocalChange()";
    }

    public int hashCode() {
      return 6171771;
    }

    public boolean equals(Object obj) {
      return (obj instanceof IsLocalChangeFilter);
    }
  }


  private static class TextContainsFilter implements Filter {
    private final ArtifactPointer myAttribute;
    private final String myPattern;

    public TextContainsFilter(ArtifactPointer attribute, String pattern) {
      myAttribute = attribute;
      myPattern = Util.upper(pattern);
    }

    public boolean accept(Revision revision) {
      String value = revision.getValue(myAttribute, String.class);
      if (value == null)
        return false;
      return StringUtil.indexOfIgnoreCase(value, myPattern) >= 0;
    }

    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      final TextContainsFilter that = (TextContainsFilter) o;

      if (myAttribute != null ? !myAttribute.equals(that.myAttribute) : that.myAttribute != null)
        return false;
      if (myPattern != null ? !myPattern.equals(that.myPattern) : that.myPattern != null)
        return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (myAttribute != null ? myAttribute.hashCode() : 0);
      result = 29 * result + (myPattern != null ? myPattern.hashCode() : 0);
      return result;
    }
  }
}
