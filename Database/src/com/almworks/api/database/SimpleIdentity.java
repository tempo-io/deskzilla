package com.almworks.api.database;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SimpleIdentity implements Identity {
  private final Address myAddress;
  private final SortedMap<Integer, Address> myAddresses;
  private static final SortedMap<Integer, Address> EMPTY = Collections.unmodifiableSortedMap(
    new TreeMap<Integer, Address>());


  public SimpleIdentity(String address) {
    this(new Address(address));
  }

  public SimpleIdentity(Address address) {
    this.myAddress = address;
    SortedMap<Integer, Address> addresses = new TreeMap<Integer, Address>();
    addresses.put(100, myAddress);
    myAddresses = Collections.unmodifiableSortedMap(addresses);
  }

  public SortedMap<Integer, Address> getAddresses() {
    return myAddresses;
  }

  public SortedMap<Integer, Address> getAddressesInDomain(Address domain) {
    if (myAddress.belongsToDomain(domain))
      return myAddresses;
    else
      return EMPTY;
  }

  public Address getDefaultAddress() {
    return myAddress;
  }

  public Address getDefaultAddressInDomain(Address domain) {
    if (myAddress.belongsToDomain(domain))
      return myAddress;
    else
      return null;
  }

  public boolean hasAddress(Address address) {
    return myAddress.equals(address);
  }

  public boolean hasAddressInDomain(Address domain) {
    return myAddress.belongsToDomain(domain);
  }

  public boolean equals(Object o) {
    if (o == null || !(o instanceof Identity))
      return false;
    return ((Identity) o).hasAddress(myAddress);
  }

  public int hashCode() {
    // :todo: complicated registry-based hashcode counting
    return myAddress.hashCode(); // works for simple identity only
  }

  public String toString() {
    return myAddress.toString();
  }
}
