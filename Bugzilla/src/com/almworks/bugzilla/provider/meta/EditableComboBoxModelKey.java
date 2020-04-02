package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.ItemKey;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;

public class EditableComboBoxModelKey extends ComboBoxModelKey {
  private final TextResolver myResolver;

  public EditableComboBoxModelKey(SingleEnumAttribute enumAttr, TextResolver resolver, ItemKey nullValue) {
    super(enumAttr, nullValue, true);
    myResolver = resolver;
  }

  public TextResolver getResolver() {
    return myResolver;
  }

  public int compare(ItemKey o1, ItemKey o2) {
    return String.CASE_INSENSITIVE_ORDER.compare(o1.getDisplayName(), o2.getDisplayName());
  }
}
