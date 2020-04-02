package com.almworks.api.database.typed;

import com.almworks.api.database.ValueType;


/**
 * Attributes compose the structure of an Artifact. Artifact's Revision contains values for one or more attributes.
 * <p/>
 * (Class Attribute is a kludge. In a real closed OO dbms (see core0), attributes are first-class objects as
 * well as artifacts.)
 *
 * @author sereda
 */
public interface Attribute extends NamedArtifact {
  ValueTypeDescriptor getValueTypeDescriptor();

  ValueType getValueType();
}
