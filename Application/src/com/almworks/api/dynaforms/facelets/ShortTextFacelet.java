package com.almworks.api.dynaforms.facelets;

import com.almworks.api.application.ModelKey;
import com.almworks.api.dynaforms.AbstractFacelet;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.items.api.DBAttribute;

public class ShortTextFacelet extends AbstractFacelet {
  private final ModelKey<String> myModelKey;
  private final DBAttribute<String> myAttribute;

  public ShortTextFacelet(String id, String shortId, String displayName, ModelKey<String> modelKey,
    DBAttribute<String> attribute)
  {
    super(id, shortId, displayName);
    myModelKey = modelKey;
    myAttribute = attribute;
  }

  public EditPrimitive createEditPrimitive() {
    return new ShortTextField(myModelKey, myAttribute);
  }
}
