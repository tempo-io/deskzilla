package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;

import java.util.*;

public class DependencyExtractor219Tests extends BaseTestCase {
  public static final String SCRIPT_219 = "\n" +
    "var first_load = true;         \n" +
    "var last_sel = new Array();    \n" +
    "\n" +
    "var useclassification = true;\n" +
    "var prods = new Array();\n" +
    "var cpts = new Array();\n" +
    "var vers = new Array();\n" +
    "var tms = new Array();\n" +
    "\n" +
    "\n" +
    "\n" +
    "  prods[0] = ['Jumber' ];\n" +
    "  prods[1] = ['TestProduct' ];\n" +
    "\n" +
    "\n" +
    "\n" +
    "  prods['Jumber'] = 0\n" +
    "  cpts[0] = ['Jumber 1', 'Mumber 2' ];\n" +
    "  vers[0] = ['unspecified' ];\n" +
    "  tms[0]  = ['---' ];\n" +
    "  prods['TestProduct'] = 1\n" +
    "  cpts[1] = ['O\\'Component', 'TestComponent', 'TestComponent2' ];\n" +
    "  vers[1] = ['other', 'V2', 'V\\x40' ];\n" +
    "  tms[1]  = ['---' ];\n" +
    "\n" +
    "/*\n" +
    " * doOnSelectProduct determines which selection should get updated \n" +
    " *\n" +
    " * - selectmode = 0  - init\n" +
    " *   selectmode = 1  - classification selected\n" +
    " *   selectmode = 2  - product selected\n" +
    " *\n" +
    " * globals:\n" +
    " *   queryform - string holding the name of the selection form\n" +
    " */\n" +
    "function doOnSelectProduct(selectmode) {\n" +
    "    var f = document.forms[queryform];\n" +
    "    milestone = (typeof(f.target_milestone) == \"undefined\" ? \n" +
    "                                               null : f.target_milestone);\n" +
    "    if (selectmode == 0) {\n" +
    "        if (useclassification) {\n" +
    "            selectClassification(f.classification, f.product, f.component, f.version, milestone);\n" +
    "        } else {\n" +
    "            selectProduct(f.product, f.component, f.version, milestone);\n" +
    "        }\n" +
    "    } else if (selectmode == 1) {\n" +
    "        selectClassification(f.classification, f.product, f.component, f.version, milestone);\n" +
    "    } else {\n" +
    "        selectProduct(f.product, f.component, f.version, milestone);\n" +
    "    }\n" +
    "}";
  private CollectionsCompare myCompare;

  protected void setUp() throws Exception {
    myCompare = new CollectionsCompare();
  }

  protected void tearDown() throws Exception {
    myCompare = null;
  }

  public void test219() {
    DependencyExtractor219 extractor = new DependencyExtractor219();
    BugzillaLists info = new BugzillaLists();
    info.getStringList(BugzillaAttribute.PRODUCT).addAll(Arrays.asList(new String[]{"Jumber", "TestProduct"}));
    info.getStringList(BugzillaAttribute.COMPONENT).addAll(
      Arrays.asList(new String[]{"Jumber 1", "Mumber 2", "O'Component", "TestComponent", "TestComponent2"}));
    info.getStringList(BugzillaAttribute.VERSION).addAll(
      Arrays.asList(new String[]{"unspecified", "other", "V2", "V@"}));
    info.getStringList(BugzillaAttribute.TARGET_MILESTONE).addAll(Arrays.asList(new String[]{"---"}));

    boolean success = extractor.extractDependencies(SCRIPT_219, info);

    assertTrue(success);

    Map<String, List<String>> map;
    map = info.getProductDependencyMap(BugzillaAttribute.COMPONENT);
    myCompare.unordered(map.get("Jumber"), new String[]{"Jumber 1", "Mumber 2"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"O'Component", "TestComponent", "TestComponent2"});
    map = info.getProductDependencyMap(BugzillaAttribute.VERSION);
    myCompare.unordered(map.get("Jumber"), new String[]{"unspecified"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"other", "V2", "V@"});
    map = info.getProductDependencyMap(BugzillaAttribute.TARGET_MILESTONE);
    myCompare.unordered(map.get("Jumber"), new String[]{"---"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"---"});
  }
}
