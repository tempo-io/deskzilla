package com.almworks.api.dynaforms;

public interface Facelet {
  String getId();

  String getShortId();

  String getDisplayName();

  EditPrimitive createEditPrimitive();
}
