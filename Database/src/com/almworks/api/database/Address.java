package com.almworks.api.database;

import java.net.URI;
import java.net.URISyntaxException;


/**
 * Address format is URI.
 *
 * @author sereda
 */
public class Address {
  private static final char DELIMITER = ':';
  protected final String myValue;

  public Address(String value) {
    checkValue(value);
    myValue = value;
  }

  protected Address createAddress(String value) {
    return new Address(value);
  }

  private void checkValue(String value) {
    if (value == null)
      throw new IllegalArgumentException("value == null");

    try {
      new URI(value);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("invalid address [" + value + "]: " + e);
    }
    // :todo: more checks?
  }

  public Address getContainingDomain() {
    int k = myValue.lastIndexOf(DELIMITER);
    if (k <= 0)
      return null; // no parent domain
    return createAddress(myValue.substring(0, k));
  }

  public boolean belongsToDomain(Address domain) {
    return myValue.startsWith(domain.getPrefix());
  }

  public String getFullString() {
    return myValue;
  }

  public String getLocalString() {
    int k = myValue.lastIndexOf(DELIMITER);
    if (k < 0)
      return myValue;
    return myValue.substring(k + 1);
  }

  public String getLocalString(Address domain) {
    String prefix = domain.getPrefix();
    if (myValue.startsWith(prefix))
      return myValue.substring(prefix.length());
    else
      return null;
  }

  public String getPrefix() {
    return getFullString() + DELIMITER;
  }

  public Address getSubAddress(String subLocalPart) {
    return subLocalPart == null ? this : createAddress(getPrefix() + subLocalPart);
  }

  public Address getSubAddress(Address subLocalPart) {
    return subLocalPart == null ? this : createAddress(getPrefix() + subLocalPart.getFullString());
  }

  public String toString() {
    return myValue;
  }

  public int hashCode() {
    return myValue.hashCode();
  }

  public boolean equals(Object o) {
    if (o == null || !(o instanceof Address))
      return false;
    return myValue.equals(((Address) o).myValue);
  }

  public static String makeValidLocalPart(String name) {
    // :todo:search for something else besides spaces
    if (name.indexOf(' ') >= 0)
      name = name.replaceAll(" ", "");
    return name;
  }
}
