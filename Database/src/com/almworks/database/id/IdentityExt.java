package com.almworks.database.id;

import com.almworks.api.database.Address;
import com.almworks.api.database.Identity;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface IdentityExt extends Identity {
  void addAddress(Address address, int priority);
}
