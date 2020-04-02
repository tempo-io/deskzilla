package com.almworks.bugzilla.provider;

import com.almworks.util.BadFormatException;
import com.almworks.util.config.JDOMConfigurator;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Failure;

import java.io.IOException;
import java.util.Arrays;

public class OurConfigurationTest extends BaseTestCase {
  public void testLimitByProduct779() {
    checkProducts("<config>" +
      "<limitByProductProducts>P1 ; P2 ,    P3, P4   ;P5-P6</limitByProductProducts>" +
      "</config>",
      new String[]{"P1", "P2", "P3", "P4", "P5-P6"});
  }

  public void testLimitByProduct() {
    checkProducts("<config>" +
      "<limitProduct>P1</limitProduct>" +
      "<limitProduct>P2</limitProduct>" +
      "<limitProduct>P3</limitProduct>" +
      "<limitProduct>P4</limitProduct>" +
      "</config>",
      new String[]{"P1", "P2", "P3", "P4"});
  }

  public void testLimitByProductConflicting() {
    checkProducts("<config>" +
      "<limitProduct>P1</limitProduct>" +
      "<limitProduct>P2</limitProduct>" +
      "<limitProduct>P3</limitProduct>" +
      "<limitProduct>P4</limitProduct>" +
      "<limitByProductProducts>P1 P2     P3, P4   P5-P6</limitByProductProducts>" +
      "</config>",
      new String[]{"P1", "P2", "P3", "P4"});
  }

  private static void checkProducts(String xml, String[] expected) {
    try {
      ReadonlyConfiguration config = JDOMConfigurator.parse(xml);
      String[] products = OurConfiguration.getLimitingProducts(config);
      CollectionsCompare compare = new CollectionsCompare();
      compare.unordered(Arrays.asList(products), expected);
    } catch (IOException e) {
      throw new Failure(e);
    } catch (BadFormatException e) {
      throw new Failure(e);
    }
  }
}
