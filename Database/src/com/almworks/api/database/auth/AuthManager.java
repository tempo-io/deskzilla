package com.almworks.api.database.auth;

import java.security.Principal;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface AuthManager {
  Principal setCurrentPrincipal(Principal principal);

  Principal getCurrentPrincipal();

  void runAs(Principal principal, Runnable runnable);

  void checkAccess(Principal principal, AuthorizedAction authAction);

  void installAuthenticationProvider(AuthProvider provider);

  void removeAuthenticationProvider(AuthProvider provider);
}
