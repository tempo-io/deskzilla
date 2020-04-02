package com.almworks.bugzilla.provider.datalink;

import com.almworks.items.api.DBAttribute;

public interface AttributeLink<T> extends DataLink, RemoteSearchable {
  DBAttribute<T> getWorkspaceAttribute();
}
