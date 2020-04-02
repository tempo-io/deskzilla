package com.almworks.api.database.auth;

import java.security.Principal;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface AuthProvider {
  Principal authenticate(AuthenticationData authData);

  UIDataBuilder<AuthenticationData> getUIDataBuilder();
}
