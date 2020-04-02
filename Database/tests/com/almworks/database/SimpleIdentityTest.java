package com.almworks.database;

import com.almworks.api.database.*;
import com.almworks.util.tests.BaseTestCase;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SimpleIdentityTest extends BaseTestCase {
  public void testIdentity() {
    final String name = "a:b:c:d";
    Address address = new Address(name);
    Identity id = new SimpleIdentity(name);

    assertTrue(id.equals(new SimpleIdentity(name)));
    assertTrue(id.hashCode() == new SimpleIdentity(name).hashCode());
    assertTrue(id.getAddresses().values().iterator().next().equals(address));
    assertTrue(id.hasAddress(address));
    assertTrue(!id.hasAddress(address.getSubAddress("e")));
    assertTrue(id.hasAddressInDomain(address.getContainingDomain()));
    assertTrue(!id.hasAddressInDomain(address.getContainingDomain().getContainingDomain().getSubAddress("e")));
    assertTrue(id.getDefaultAddress().getFullString().equals(name));
  }
}
