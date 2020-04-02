package com.almworks.bugzilla.provider.workflow;

import com.almworks.api.application.ModelOperation;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.explorer.workflow.SetComboBoxParam;
import com.almworks.util.text.NameMnemonic;

import java.util.Collection;

class UserEditPrimitive extends SetComboBoxParam {
  UserEditPrimitive(
    String attribute, NameMnemonic mn, boolean editable,
    Collection<String> exclusions, Collection<String> inclusions)
  {
    super(attribute, ModelOperation.SET_ITEM_KEY, mn, editable, exclusions, inclusions);
    setCanvasRenderer(User.RENDERER);
    setCaseSensitive(false);
  }
}
