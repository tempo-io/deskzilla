package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.application.ResolvedItem;
import com.almworks.util.commons.Enabled;

public class ResolvedUser extends ResolvedItem implements Enabled {
  private final boolean myEnabled;

  public ResolvedUser(ResolvedItem item, boolean enabled) {
    super(
      item.getResolvedItem(), item.getDisplayName(), item.getOrder(),
      item.getId(), item.getIcon(), item.getConnectionItem());
    myEnabled = enabled;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public boolean isSame(ResolvedItem that) {
    return super.isSame(that) && (myEnabled == ((ResolvedUser)that).myEnabled);
  }
}
