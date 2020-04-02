package com.almworks.api.dynaforms;

import com.almworks.items.api.DBAttribute;

import javax.swing.*;
import java.util.List;

public interface AbstractWorkflowField<T extends JComponent> extends EditPrimitive<T> {
  void addAffectedFields(List<DBAttribute> fieldList);
}
