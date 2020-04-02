package com.almworks.explorer.tree;

import com.almworks.api.application.TagsUtil;
import com.almworks.util.config.Configuration;
import com.almworks.util.ui.actions.ActionContext;

public class FavoritesNode extends TagNodeImpl {
  public FavoritesNode(Configuration configuration) {
    super(new MyPresentation("Favorites", TagsUtil.FAVORITES_ICONPATH), configuration);
  }

  public boolean isRenamable() {
    return false;
  }

  public boolean isRemovable() {
    return false;
  }

  @Override
  public boolean isEditable() {
    return false;
  }

  public boolean editNode(ActionContext context) {
    // ignore
    return false;
  }
}
