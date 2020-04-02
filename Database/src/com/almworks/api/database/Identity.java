package com.almworks.api.database;

import java.util.SortedMap;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface Identity {
  SortedMap<Integer, Address> getAddresses();

  SortedMap<Integer, Address> getAddressesInDomain(Address domain);

  Address getDefaultAddress();

  Address getDefaultAddressInDomain(Address domain);

  boolean hasAddress(Address address);

  boolean hasAddressInDomain(Address domain);
}
