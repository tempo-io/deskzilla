package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.items.api.DBAttribute;
import org.almworks.util.Util;

import java.math.BigDecimal;

public class DecimalLink extends ScalarLink<BigDecimal> {
  private static final BigDecimal ZERO = BigDecimal.valueOf(0);

  public DecimalLink(DBAttribute<BigDecimal> attribute, BugzillaAttribute bugzillaAttribute,
    boolean ignoreEmpty, boolean inPrototype) {
    super(attribute, bugzillaAttribute, ignoreEmpty, inPrototype, false);
  }

  protected boolean detectFailedUpdateValues(String requestedValue, String newValue) {
    try {
      BigDecimal d1 = requestedValue == null ? ZERO : new BigDecimal(requestedValue);
      BigDecimal d2 = newValue == null ? ZERO : new BigDecimal(newValue);
      return d1.compareTo(d2) == 0;
    } catch (NumberFormatException e) {
      return Util.equals(requestedValue, newValue);
    }
  }
}
