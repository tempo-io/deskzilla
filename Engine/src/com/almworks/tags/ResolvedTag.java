package com.almworks.tags;

import com.almworks.api.application.*;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public class ResolvedTag extends ResolvedItem {
  @Nullable
  private final String myIconPath;

  public ResolvedTag(@NotNull long item, @NotNull String representation, ItemOrder order, @Nullable String iconPath) {
    super(item, representation, order, null, TagsUtil.getTagIcon(iconPath, false), 0L);
    myIconPath = iconPath;
  }

  public boolean isSame(ResolvedItem that) {
    if (!super.isSame(that))
      return false;
    return Util.equals(myIconPath, ((ResolvedTag) that).myIconPath);
  }

  @Nullable
  public String getIconPath() {
    return myIconPath;
  }
}

