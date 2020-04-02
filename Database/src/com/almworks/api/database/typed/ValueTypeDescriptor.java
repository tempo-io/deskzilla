package com.almworks.api.database.typed;

import com.almworks.api.database.ValueType;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ValueTypeDescriptor extends NamedArtifact {
  ValueType getValueType();
}
