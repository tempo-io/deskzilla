package com.almworks.database.filter;

import com.almworks.api.database.ArtifactPointer;
import com.almworks.api.database.Filter;
import com.almworks.api.database.util.WorkspaceUtils;
import com.almworks.database.Basis;

/**
 * :todoc:
 *
 * @author sereda
 */
abstract class AttributeBasedFilter extends SystemFilter {
  protected final ArtifactPointer myAttribute;
  private static final Filter[] EMPTY = {};
  private final boolean myIndexable;

  protected AttributeBasedFilter(Basis basis, ArtifactPointer attribute, boolean indexable) {
    super(basis);
    myIndexable = indexable;
    assert attribute != null;
    myAttribute = attribute;
  }

  public boolean isIndexable() {
    return myIndexable;
  }

  public boolean isComposite() {
    return false;
  }

  public FilterType getType() {
    return FilterType.LEAF;
  }

  public Filter[] getChildren() {
    return EMPTY;
  }

  protected long getAttributeID() {
    return myAttribute.getPointerKey();
  }

  public int hashCode() {
    return getClass().hashCode() * 23 + myAttribute.hashCode();
  }

  public boolean equals(Object o) {
    if (o == null)
      return false;
    if (!getClass().equals(o.getClass()))
      return false;
    return WorkspaceUtils.equals(myAttribute, ((AttributeBasedFilter) o).myAttribute);
  }
}
