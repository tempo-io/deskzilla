package com.almworks.database;

import com.almworks.api.database.Address;
import com.almworks.util.tests.BaseTestCase;

/**
 * :todoc:
 *
 * @author sereda
 */
public class AddressTest extends BaseTestCase {
  protected Address address;

  protected void setUp() throws Exception {
    address = new Address("abc:xyz:foo");
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testMinor() {
    assertTrue(address.belongsToDomain(new Address("abc")));
    assertTrue(address.belongsToDomain(new Address("abc:xyz")));
    assertTrue(!address.belongsToDomain(new Address("abc:xyz:foo")));
    assertTrue(!address.belongsToDomain(new Address("abc:xyz123")));
    assertTrue(address.getFullString().equals("abc:xyz:foo"));
    assertTrue(address.getLocalString().indexOf(':') == -1);
    assertTrue(address.getLocalString(new Address("haba:haba")) == null);
    assertTrue(address.getLocalString(new Address("abc")).equals("xyz:foo"));
    assertTrue(address.getSubAddress("bar").equals(new Address("abc:xyz:foo:bar")));
  }

  public void testContainingDomain() {
    Address addr = address.getContainingDomain();
    addr = addr.getSubAddress(address.getLocalString());
    assertTrue(address.equals(addr));
  }
}
