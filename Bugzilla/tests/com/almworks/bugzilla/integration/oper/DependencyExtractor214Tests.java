package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;

import java.util.*;

public class DependencyExtractor214Tests extends BaseTestCase {
  public static final String SCRIPT_214 = "var cpts = new Array();\n" +
    "var vers = new Array();\n" +
    "var tms  = new Array();\n" +
    "cpts['BugGenerator'] = new Array();\n" +
    "cpts['Buggy'] = new Array();\n" +
    "cpts['Component Habba'] = new Array();\n" +
    "cpts['TestComponent'] = new Array();\n" +
    "cpts['Three Words Component'] = new Array();\n" +
    "vers['---'] = new Array();\n" +
    "vers['other'] = new Array();\n" +
    "vers['unspecified'] = new Array();\n" +
    "vers['V@#'] = new Array();\n" +
    "tms['---'] = new Array();\n" +
    "tms['M1'] = new Array();\n" +
    "tms['M2'] = new Array();\n" +
    "cpts['Component Habba'][cpts['Component Habba'].length] = 'Habba Habba';\n" +
    "vers['unspecified'][vers['unspecified'].length] = 'Habba Habba';\n" +
    "tms['---'][tms['---'].length] = 'Habba Habba';\n" +
    "cpts['BugGenerator'][cpts['BugGenerator'].length] = 'Test Product';\n" +
    "cpts['TestComponent'][cpts['TestComponent'].length] = 'Test Product';\n" +
    "vers['other'][vers['other'].length] = 'Test Product';\n" +
    "vers['V@#'][vers['V@#'].length] = 'Test Product';\n" +
    "tms['---'][tms['---'].length] = 'Test Product';\n" +
    "tms['M1'][tms['M1'].length] = 'Test Product';\n" +
    "tms['M2'][tms['M2'].length] = 'Test Product';\n" +
    "cpts['Three Words Component'][cpts['Three Words Component'].length] = 'Three Words Product';\n" +
    "vers['---'][vers['---'].length] = 'Three Words Product';\n" +
    "vers['unspecified'][vers['unspecified'].length] = 'Three Words Product';\n" +
    "tms['---'][tms['---'].length] = 'Three Words Product';\n" +
    "cpts['Buggy'][cpts['Buggy'].length] = 'TooManyBugger';\n" +
    "vers['unspecified'][vers['unspecified'].length] = 'TooManyBugger';\n" +
    "tms['---'][tms['---'].length] = 'TooManyBugger';\n" +
    "\n" +
    "// Only display versions/components valid for selected product(s)\n" +
    "\n" +
    "function selectProduct(f) {\n" +
    "    // Netscape 4.04 and 4.05 also choke with an \"undefined\"\n" +
    "    // error.  if someone can figure out how to \"define\" the\n" +
    "    // whatever, we'll remove this hack.  in the mean time, we'll\n" +
    "    // assume that v4.00-4.03 also die, so we'll disable the neat\n" +
    "    // javascript stuff for Netscape 4.05 and earlier.\n" +
    "\n" +
    "    var cnt = 0;\n" +
    "    var i;\n" +
    "    var j;\n" +
    "\n" +
    "    if (!f) {\n" +
    "        return;\n" +
    "    }\n" +
    "\n" +
    "    for (i=0 ; i<f.product.length ; i++) {\n" +
    "        if (f.product[i].selected) {\n" +
    "            cnt++;\n" +
    "        }\n" +
    "    }\n" +
    "    var doall = (cnt == f.product.length || cnt == 0);\n" +
    "\n" +
    "    var csel = new Object();\n" +
    "    for (i=0 ; i<f.component.length ; i++) {\n" +
    "        if (f.component[i].selected) {\n" +
    "            csel[f.component[i].value] = 1;\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "    f.component.options.length = 0;\n" +
    "\n" +
    "    for (c in cpts) {\n" +
    "        if (typeof(cpts[c]) == 'function') continue;\n" +
    "        var doit = doall;\n" +
    "        for (i=0 ; !doit && i<f.product.length ; i++) {\n" +
    "            if (f.product[i].selected) {\n" +
    "                var p = f.product[i].value;\n" +
    "                for (j in cpts[c]) {\n" +
    "                    if (typeof(cpts[c][j]) == 'function') continue;\n" +
    "                    var p2 = cpts[c][j];\n" +
    "                    if (p2 == p) {\n" +
    "                        doit = true;\n" +
    "                        break;\n" +
    "                    }\n" +
    "                }\n" +
    "            }\n" +
    "        }\n" +
    "        if (doit) {\n" +
    "            var l = f.component.length;\n" +
    "            f.component[l] = new Option(c, c);\n" +
    "            if (csel[c]) {\n" +
    "                f.component[l].selected = true;\n" +
    "            }\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "    var vsel = new Object();\n" +
    "    for (i=0 ; i<f.version.length ; i++) {\n" +
    "        if (f.version[i].selected) {\n" +
    "            vsel[f.version[i].value] = 1;\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "    f.version.options.length = 0;\n" +
    "\n" +
    "    for (v in vers) {\n" +
    "        if (typeof(vers[v]) == 'function') continue;\n" +
    "        doit = doall;\n" +
    "        for (i=0 ; !doit && i<f.product.length ; i++) {\n" +
    "            if (f.product[i].selected) {\n" +
    "                p = f.product[i].value;\n" +
    "                for (j in vers[v]) {\n" +
    "                    if (typeof(vers[v][j]) == 'function') continue;\n" +
    "                    p2 = vers[v][j];\n" +
    "                    if (p2 == p) {\n" +
    "                        doit = true;\n" +
    "                        break;\n" +
    "                    }\n" +
    "                }\n" +
    "            }\n" +
    "        }\n" +
    "        if (doit) {\n" +
    "            l = f.version.length;\n" +
    "            f.version[l] = new Option(v, v);\n" +
    "            if (vsel[v]) {\n" +
    "                f.version[l].selected = true;\n" +
    "            }\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "\n" +
    "      if (f.target_milestone) {\n" +
    "        var tmsel = new Object();\n" +
    "        for (i=0 ; i<f.target_milestone.length ; i++) {\n" +
    "            if (f.target_milestone[i].selected) {\n" +
    "                tmsel[f.target_milestone[i].value] = 1;\n" +
    "            }\n" +
    "        }\n" +
    "    \n" +
    "        f.target_milestone.options.length = 0;\n" +
    "    \n" +
    "        for (tm in tms) {\n" +
    "            if (typeof(tms[v]) == 'function') continue;\n" +
    "            doit = doall;\n" +
    "            for (i=0 ; !doit && i<f.product.length ; i++) {\n" +
    "                if (f.product[i].selected) {\n" +
    "                    p = f.product[i].value;\n" +
    "                    for (j in tms[tm]) {\n" +
    "                        if (typeof(tms[tm][j]) == 'function') continue;\n" +
    "                        p2 = tms[tm][j];\n" +
    "                        if (p2 == p) {\n" +
    "                            doit = true;\n" +
    "                            break;\n" +
    "                        }\n" +
    "                    }\n" +
    "                }\n" +
    "            }\n" +
    "            if (doit) {\n" +
    "                l = f.target_milestone.length;\n" +
    "                f.target_milestone[l] = new Option(tm, tm);\n" +
    "                if (tmsel[tm]) {\n" +
    "                    f.target_milestone[l].selected = true;\n" +
    "                }\n" +
    "            }\n" +
    "        }\n" +
    "      }\n" +
    "    }\n" +
    "// ";

  private CollectionsCompare myCompare;

  protected void setUp() throws Exception {
    myCompare = new CollectionsCompare();
  }

  protected void tearDown() throws Exception {
    myCompare = null;
  }

  public void test214() {
    DependencyExtractor214 extractor = new DependencyExtractor214();
    BugzillaLists info = new BugzillaLists();
    info.getStringList(BugzillaAttribute.PRODUCT).addAll(Arrays.asList(new String[]{"Habba Habba", "Test Product", "Three Words Product", "TooManyBugger"}));
    info.getStringList(BugzillaAttribute.COMPONENT).addAll(Arrays.asList(new String[]{"BugGenerator", "Buggy", "Component Habba", "TestComponent", "Three Words Component"}));
    info.getStringList(BugzillaAttribute.VERSION).addAll(Arrays.asList(new String[]{"---", "other", "unspecified", "V@#"}));
    info.getStringList(BugzillaAttribute.TARGET_MILESTONE).addAll(Arrays.asList(new String[]{"---", "M1", "M2"}));

    boolean success = extractor.extractDependencies(SCRIPT_214, info);

    assertTrue(success);

    Map<String, List<String>> map;
    map = info.getProductDependencyMap(BugzillaAttribute.COMPONENT);
    myCompare.unordered(map.get("Habba Habba"), new String[]{"Component Habba"});
    myCompare.unordered(map.get("Test Product"), new String[]{"BugGenerator", "TestComponent"});
    myCompare.unordered(map.get("Three Words Product"), new String[]{"Three Words Component"});
    myCompare.unordered(map.get("TooManyBugger"), new String[]{"Buggy"});
    map = info.getProductDependencyMap(BugzillaAttribute.VERSION);
    myCompare.unordered(map.get("Habba Habba"), new String[]{"unspecified"});
    myCompare.unordered(map.get("Test Product"), new String[]{"other", "V@#"});
    myCompare.unordered(map.get("Three Words Product"), new String[]{"---", "unspecified"});
    myCompare.unordered(map.get("TooManyBugger"), new String[]{"unspecified"});
    map = info.getProductDependencyMap(BugzillaAttribute.TARGET_MILESTONE);
    myCompare.unordered(map.get("Habba Habba"), new String[]{"---"});
    myCompare.unordered(map.get("Test Product"), new String[]{"---", "M1", "M2"});
    myCompare.unordered(map.get("Three Words Product"), new String[]{"---"});
    myCompare.unordered(map.get("TooManyBugger"), new String[]{"---"});
  }
}
