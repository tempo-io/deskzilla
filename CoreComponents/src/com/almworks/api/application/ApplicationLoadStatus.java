package com.almworks.api.application;

import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;

public interface ApplicationLoadStatus {
  Role<ApplicationLoadStatus> ROLE = Role.role(ApplicationLoadStatus.class);

  /**
   * @return model that gets set to true when application is loaded
   * (whatever that means)
   */
  ScalarModel<Boolean> getApplicationLoadedModel();
}
