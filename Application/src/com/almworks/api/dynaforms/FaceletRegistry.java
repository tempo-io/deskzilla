package com.almworks.api.dynaforms;

import com.almworks.util.properties.Role;
import org.almworks.util.detach.Lifespan;

public interface FaceletRegistry {
  Role<FaceletRegistry> ROLE = Role.role("faceletRegistry");

  void registerFacelet(Lifespan life, Facelet facelet);

  void unregisterFacelet(Facelet facelet);
}
