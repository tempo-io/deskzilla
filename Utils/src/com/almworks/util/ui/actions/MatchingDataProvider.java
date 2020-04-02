package com.almworks.util.ui.actions;

import com.almworks.util.components.DataProviders;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class MatchingDataProvider extends AbstractDataProvider {
  private final List<Object> myData = Collections15.arrayList();

  public MatchingDataProvider(TypedKey<?> ... roles) {
    super(roles);
  }

  protected MatchingDataProvider(Collection<? extends TypedKey<?>> roles) {
    super(roles);
  }

  public boolean hasRole(@NotNull TypedKey<?> role) {
    return (role instanceof DataRole) && super.hasRole(role);
  }

  public void setData(List<?> data) {
    myData.clear();
    for (Object o : data) {
      if (o != null)
        myData.add(o);
    }
    fireChangedAll();
  }

  public void setSingleData(Object data) {
    if (data != null)
      setData(Collections.singletonList(data));
    else
      setData(Collections15.emptyList());
  }

  public <T> List<T> getObjectsByRole(TypedKey<? extends T> role) {
    assert hasRole(role) : role;
    DataRole<? extends T> dataRole = (DataRole<? extends T>) role;
    return DataProviders.ensureMatchesAll(dataRole, Collections15.arrayList(myData));
  }
}
