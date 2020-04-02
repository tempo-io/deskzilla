package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugzillaLists;
import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;

import java.util.*;

public class DependencyExtractor216Tests extends BaseTestCase {
  public static final String SCRIPT_216 = "\n" +
    "var first_load = true;         \n" +
    "var last_sel = new Array();    \n" +
    "\n" +
    "var cpts = new Array();\n" +
    "var vers = new Array();\n" +
    "var tms = new Array();\n" +
    "\n" +
    "\n" +
    "\n" +
    "  cpts[0] = [ 'AAAA' ];\n" +
    "  vers[0] = [ 'unspecified' ];\n" +
    "  tms[0]  = [ '---' ];\n" +
    "  cpts[1] = [ 'TestComponent' ];\n" +
    "  vers[1] = [ 'other', 'V1', 'V2' ];\n" +
    "  tms[1]  = [ '---' ];\n" +
    "\n" +
    "\n" +
    "\n" +
    "function updateSelect(array, sel, target, merging) {\n" +
    "        \n" +
    "    var i, item;\n" +
    "\n" +
    "    \n" +
    "    if (array.length < 1) {\n" +
    "        target.options.length = 0;\n" +
    "        return false;\n" +
    "    }\n" +
    "\n" +
    "    if (merging) {\n" +
    "        \n" +
    "        \n" +
    "        item = merge_arrays(array[sel[0]], target.options, 1);\n" +
    "\n" +
    "        \n" +
    "        for (i = 1 ; i < sel.length ; i++) {\n" +
    "            item = merge_arrays(array[sel[i]], item, 0);\n" +
    "        }\n" +
    "    } else if ( sel.length > 1 ) {\n" +
    "        \n" +
    "        item = merge_arrays(array[sel[0]],array[sel[1]], 0);\n" +
    "\n" +
    "        \n" +
    "        for (i = 2; i < sel.length; i++) {\n" +
    "            item = merge_arrays(item, array[sel[i]], 0);\n" +
    "        }\n" +
    "    } else { \n" +
    "        item = array[sel[0]];\n" +
    "    }\n" +
    "\n" +
    "    \n" +
    "    target.options.length = 0;\n" +
    "\n" +
    "    \n" +
    "    for (i = 0; i < item.length; i++) {\n" +
    "        target.options[i] = new Option(item[i], item[i]);\n" +
    "    }\n" +
    "    return true;\n" +
    "}\n" +
    "\n" +
    "\n" +
    "function fake_diff_array(a, b) {\n" +
    "    var newsel = new Array();\n" +
    "    var found = false;\n" +
    "\n" +
    "    \n" +
    "    for (var ia in a) {\n" +
    "        for (var ib in b) {\n" +
    "            if (a[ia] == b[ib]) {\n" +
    "                found = true;\n" +
    "            }\n" +
    "        }\n" +
    "        if (!found) {\n" +
    "            newsel[newsel.length] = a[ia];\n" +
    "        }\n" +
    "        found = false;\n" +
    "    }\n" +
    "    return newsel;\n" +
    "}\n" +
    "\n" +
    "\n" +
    "function merge_arrays(a, b, b_is_select) {\n" +
    "    var pos_a = 0;\n" +
    "    var pos_b = 0;\n" +
    "    var ret = new Array();\n" +
    "    var bitem, aitem;\n" +
    "\n" +
    "    \n" +
    "    while ((pos_a < a.length) && (pos_b < b.length)) {\n" +
    "        if (b_is_select) {\n" +
    "            bitem = b[pos_b].value;\n" +
    "        } else {\n" +
    "            bitem = b[pos_b];\n" +
    "        }\n" +
    "        aitem = a[pos_a];\n" +
    "\n" +
    "        \n" +
    "        if (aitem.toLowerCase() < bitem.toLowerCase()) {\n" +
    "            ret[ret.length] = aitem;\n" +
    "            pos_a++;\n" +
    "        } else {\n" +
    "            \n" +
    "            if (aitem.toLowerCase() > bitem.toLowerCase()) {\n" +
    "                ret[ret.length] = bitem;\n" +
    "                pos_b++;\n" +
    "            } else {\n" +
    "                \n" +
    "                ret[ret.length] = aitem;\n" +
    "                pos_a++;\n" +
    "                pos_b++;\n" +
    "            }\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "    \n" +
    "    if (pos_a < a.length) {\n" +
    "        for (; pos_a < a.length ; pos_a++) {\n" +
    "            ret[ret.length] = a[pos_a];\n" +
    "        }\n" +
    "    }\n" +
    "\n" +
    "    if (pos_b < b.length) {\n" +
    "        for (; pos_b < b.length; pos_b++) {\n" +
    "            if (b_is_select) {\n" +
    "                bitem = b[pos_b].value;\n" +
    "            } else {\n" +
    "                bitem = b[pos_b];\n" +
    "            }\n" +
    "            ret[ret.length] = bitem;\n" +
    "        }\n" +
    "    }\n" +
    "    return ret;\n" +
    "}\n" +
    "\n" +
    "\n" +
    "function get_selection(control, findall, want_values) {\n" +
    "    var ret = new Array();\n" +
    "\n" +
    "    if ((!findall) && (control.selectedIndex == -1)) {\n" +
    "        return ret;\n" +
    "    }\n" +
    "\n" +
    "    for (var i=0; i<control.length; i++) {\n" +
    "        if (findall || control.options[i].selected) {\n" +
    "            ret[ret.length] = want_values ? control.options[i].value : i;\n" +
    "        }\n" +
    "    }\n" +
    "    return ret;\n" +
    "}\n" +
    "\n" +
    "\n" +
    "function restoreSelection(control, selnames) {\n" +
    "    \n" +
    "    for (var j=0; j < selnames.length; j++) {\n" +
    "        for (var i=0; i < control.options.length; i++) {\n" +
    "            if (control.options[i].value == selnames[j]) {\n" +
    "                control.options[i].selected = true;\n" +
    "            }\n" +
    "        }\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "\n" +
    "function selectProduct(f) {\n" +
    "    \n" +
    "    if ((!f) || (!f.product)) {\n" +
    "        return;\n" +
    "    }\n" +
    "\n" +
    "    \n" +
    "    if ((first_load) && (f.product.selectedIndex == -1)) {\n" +
    "        first_load = false;\n" +
    "        return;\n" +
    "    }\n" +
    "    \n" +
    "    \n" +
    "    first_load = false;\n" +
    "\n" +
    "    \n" +
    "    var merging = false;\n" +
    "    var sel = Array();\n" +
    "\n" +
    "    \n" +
    "    var findall = f.product.selectedIndex == -1;\n" +
    "    sel = get_selection(f.product, findall, false);\n" +
    "    if (!findall) {\n" +
    "        \n" +
    "        var tmp = sel;\n" +
    "    \n" +
    "        \n" +
    "        if ((last_sel.length > 0) && (last_sel.length < sel.length)) {\n" +
    "            sel = fake_diff_array(sel, last_sel);\n" +
    "            merging = true;\n" +
    "        }\n" +
    "        last_sel = tmp;\n" +
    "    }\n" +
    "    \n" +
    "    var saved_cpts = get_selection(f.component, false, true);\n" +
    "    var saved_vers = get_selection(f.version, false, true);\n" +
    "    var saved_tms = get_selection(f.target_milestone, false, true);\n" +
    "\n" +
    "    \n" +
    "    updateSelect(cpts, sel, f.component, merging);\n" +
    "    restoreSelection(f.component, saved_cpts);\n" +
    "    updateSelect(vers, sel, f.version, merging);\n" +
    "    restoreSelection(f.version, saved_vers);\n" +
    "    updateSelect(tms, sel, f.target_milestone, merging);\n" +
    "    restoreSelection(f.target_milestone, saved_tms);\n" +
    "}\n" +
    "";


  private CollectionsCompare myCompare;

  protected void setUp() throws Exception {
    myCompare = new CollectionsCompare();
  }

  protected void tearDown() throws Exception {
    myCompare = null;
  }

  public void test216() {
    DependencyExtractor216 extractor = new DependencyExtractor216();
    BugzillaLists info = new BugzillaLists();
    info.getStringList(BugzillaAttribute.PRODUCT).addAll(Arrays.asList(new String[]{"A%B", "TestProduct"}));
    info.getStringList(BugzillaAttribute.COMPONENT).addAll(
      Arrays.asList(new String[]{"AAAA", "TestComponent"}));
    info.getStringList(BugzillaAttribute.VERSION).addAll(
      Arrays.asList(new String[]{"unspecified", "other", "V2", "V1"}));
    info.getStringList(BugzillaAttribute.TARGET_MILESTONE).addAll(Arrays.asList(new String[]{"---"}));

    boolean success = extractor.extractDependencies(SCRIPT_216, info);

    assertTrue(success);

    Map<String, List<String>> map;
    map = info.getProductDependencyMap(BugzillaAttribute.COMPONENT);
    myCompare.unordered(map.get("A%B"), new String[]{"AAAA"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"TestComponent"});
    map = info.getProductDependencyMap(BugzillaAttribute.VERSION);
    myCompare.unordered(map.get("A%B"), new String[]{"unspecified"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"other", "V1", "V2"});
    map = info.getProductDependencyMap(BugzillaAttribute.TARGET_MILESTONE);
    myCompare.unordered(map.get("A%B"), new String[]{"---"});
    myCompare.unordered(map.get("TestProduct"), new String[]{"---"});
  }
}
