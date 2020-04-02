package com.almworks.bugzilla.integration.oper;

import com.almworks.util.files.FileUtil;
import com.almworks.util.tests.BaseTestCase;

import java.text.ParseException;
import java.util.Map;

public class RegressionProductInformationLoaderTests extends BaseTestCase {
  private static final String RESOURCE = "com/almworks/bugzilla/integration/oper/RegressionProductInformationLoader.js";

  public void test() throws ParseException {
    String js = FileUtil.loadTextResource(RESOURCE, BadCsvLoadQueryTests.class.getClassLoader());
    Map<Integer, Map<String, String>> mapping = ProductInformationLoader.extractMapping(js);
  }
}
