package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.integration.data.CustomFieldDependencies;
import com.almworks.bugzilla.integration.oper.js.JSFunctionCallWithConstantArgumentsFilter;
import com.almworks.bugzilla.integration.oper.js.JSParser;
import com.almworks.util.xml.JDOMUtils;
import org.almworks.util.*;
import org.jdom.Element;

import java.text.ParseException;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.almworks.util.Collections15.arrayList;

public class CustomFieldDependencyExtractor {
  public static CustomFieldDependencies getDependencies(Element bugPage, CustomFieldDependencies.Source source) {
    final CustomFieldDependencies valueDeps = new CustomFieldDependencies(source);
    final CustomFieldDependencies fieldDeps = new CustomFieldDependencies(source);

    Iterator<Element> ii = JDOMUtils.searchElementIterator(bugPage, "script", "type", "text/javascript");
    while (ii.hasNext()) {
      String js = JDOMUtils.getText(ii.next(), true);
      if (js.length() == 0)
        continue;
      JSParser parser = new JSParser(js);
      try {
        parser.visit(new JSFunctionCallWithConstantArgumentsFilter() {
          @Override
          protected boolean acceptFunctionName(String name) {
            return "showValueWhen".equals(name) || "showFieldWhen".equals(name);
          }

          @Override
          protected void visitFunctionCall(String name, List<Object> arguments) {
            if ("showValueWhen".equals(name)) {
              addValueDeps(arguments, valueDeps);
            } else if ("showFieldWhen".equals(name)) {
              addFieldDeps(arguments, fieldDeps);
            }
          }
        });
      } catch (ParseException e) {
        Log.debug("invalid javascript", e);
      }
    }

    if (!valueDeps.getDependencyMap().isEmpty()) {
      joinDeps(fieldDeps, valueDeps, bugPage);
    }

    return fieldDeps;
  }

  private static void joinDeps(CustomFieldDependencies target, CustomFieldDependencies valueDeps, Element bugPage) {
    // field id => (int value id => value string)
    Map<String, Map<String, String>> fieldMap = Collections15.hashMap();

    Map<String, CustomFieldDependencies.Dependency> map = valueDeps.getDependencyMap();
    for (Map.Entry<String, CustomFieldDependencies.Dependency> e : map.entrySet()) {
      String field = e.getKey();
      CustomFieldDependencies.Dependency numdep = e.getValue();
      if (numdep == null) {
        assert false : field;
        continue;
      }
      String controllerField = numdep.getValuesControllerField();
      CustomFieldDependencies.Dependency dep = target.getDependency(field);

      CustomFieldDependencies.Source source = target.getSource();
      if(!loadFields(fieldMap, field, bugPage, source)) {
        dep.setValuesControllerField(CustomFieldDependencies.UNKNOWN_OPTION);
        continue;
      }
      loadFields(fieldMap, controllerField, bugPage, source);

      boolean success = false;
      for (Map.Entry<String, String> ee : numdep.getControlledValues().entrySet()) {
        String controlled = getFieldValue(fieldMap, field, ee.getKey());
        String controller = getFieldValue(fieldMap, controllerField, ee.getValue());
        if (controlled != null) {
          success = true;
          dep.mapControlledValue(controlled, controller == null ? CustomFieldDependencies.UNKNOWN_OPTION : controller);
        }
      }
      if (success) {
        dep.setValuesControllerField(controllerField);
      } else {
        Log.warn("missing dependencies for field " + field);
      }
    }
  }

  private static String getFieldValue(Map<String, Map<String, String>> fieldMap, String field, String key) {
    Map<String, String> map = fieldMap.get(field);
    return map == null ? null : map.get(key);
  }

  private static boolean loadFields(Map<String, Map<String, String>> fieldMap, String field, Element bugPage, CustomFieldDependencies.Source source) {
    if(field == null) {
      return false;
    }

    if(fieldMap.containsKey(field)) {
      return true;
    }

    final Element select = JDOMUtils.searchElement(bugPage, "select", "name", field);
    if (select == null) {
      Log.warn("cannot load field " + field + " (no <select>) " + source);
      return false;
    }

    final Map<String,String> map = Collections15.linkedHashMap();
    for(final Iterator<Element> ii = JDOMUtils.searchElementIterator(select, "option"); ii.hasNext();) {
      final Element option = ii.next();

      final String value = JDOMUtils.getAttributeValue(option, "value", "", true);
      if(value.isEmpty()) {
        continue;
      }

      final String idstr = JDOMUtils.getAttributeValue(option, "id", "", false);
      if(idstr.endsWith(field) && idstr.startsWith("v")) {
        final int k = idstr.indexOf('_');
        if(k > 0) {
          final int id = Util.toInt(idstr.substring(1, k), -1);
          if(id >= 0) {
            map.put(String.valueOf(id), value);
          }
        }
      }
    }

    if(map.isEmpty()) {
      Log.warn("cannot load field " + field + " (no valid <option>s)");
      return false;
    }

    fieldMap.put(field, map);
    return true;
  }

  private static void addFieldDeps(List<Object> arguments, CustomFieldDependencies valueDeps) {
    try {
      String controlledField = (String) arguments.get(0);
      String controllerField = (String) arguments.get(1);
      List<String> controllerValues = getVisibilityControllerValuesList(arguments, arguments.get(2));
      CustomFieldDependencies.Dependency dep = valueDeps.getDependency(controlledField);
      dep.setVisibilityControllerField(controllerField);
      dep.setVisibilityControllerValues(controllerValues);
    } catch (ClassCastException e) {
      Log.warn(e);
    } catch (IndexOutOfBoundsException e) {
      Log.warn(e);
    } catch (NullPointerException e) {
      Log.warn(e);
    }
  }

  private static List<String> getVisibilityControllerValuesList(List<Object> arguments, Object controllerValuesRaw) {
    List<String> controllerValues;
    // Before Bugzilla 4.2
    if (controllerValuesRaw instanceof String) controllerValues = singletonList((String) controllerValuesRaw);
    // Since Bugzilla 4.2
    else if (controllerValuesRaw instanceof List) {
      List controllerValuesList = (List) controllerValuesRaw;
      controllerValues = arrayList(controllerValuesList.size());
      for (Object controllerValueRaw : controllerValuesList) controllerValues.add((String) controllerValueRaw);
    } else {
      Log.error("Cannot get custom field dependencies: " + arguments + " " + controllerValuesRaw);
      controllerValues = null;
    }
    return controllerValues;
  }

  private static void addValueDeps(List<Object> arguments, CustomFieldDependencies valueDeps) {
    try {
      String controlledField = (String) arguments.get(0);
      List controlledValueIds = (List) arguments.get(1);
      String controllerField = (String) arguments.get(2);
      Integer controllerValue = (Integer) arguments.get(3);
      CustomFieldDependencies.Dependency dep = valueDeps.getDependency(controlledField);
      dep.setValuesControllerField(controllerField);
      if (!controlledValueIds.isEmpty()) {
        for (Integer id : (List<Integer>) controlledValueIds) {
          dep.mapControlledValue(String.valueOf(id), String.valueOf(controllerValue));
        }
      }
    } catch (ClassCastException e) {
      Log.warn(e);
    } catch (IndexOutOfBoundsException e) {
      Log.warn(e);
    } catch (NullPointerException e) {
      Log.warn(e);
    }
  }
}
