package com.almworks.api.connector.http;

import com.almworks.util.collections.MultiMap;
import com.almworks.util.xml.JDOMUtils;
import org.jdom.Element;

import java.util.List;

public class ExtractFormParameters {
  public static final ExtractFormParameters FOR_SUBMIT = new ExtractFormParameters(true, true);
  /** Customer has same problem with Bugzilla 4.0.2 */
  public static final ExtractFormParameters FOR_SUBMIT_4_0 = new ExtractFormParameters(true, false);
  public static final ExtractFormParameters DEFAULT = new ExtractFormParameters(false, false);

  private final boolean myIncludeSubmit;
  private final boolean myIncludeAllSelects;

  public ExtractFormParameters(boolean includeSubmit, boolean includeAllSelects) {
    myIncludeSubmit = includeSubmit;
    myIncludeAllSelects = includeAllSelects;
  }

  public void formSelects(final Element root, MultiMap<String, String> result) {
    final List<Element> selects = JDOMUtils.searchElements(root, "select");
    for (Element selectElement : selects) {
      processSelect(selectElement, result);
    }
  }

  public void processSelect(Element selectElement, MultiMap<String, String> result) {
    String name = JDOMUtils.getAttributeValue(selectElement, "name", null, true);
    if (name != null) {
      boolean multiple = HtmlUtils.isMultipleSelect(selectElement);
      final List<Element> options = JDOMUtils.searchElements(selectElement, "option");
      boolean anySelected = false;
      for (Element optionElement : options) {
        if (optionElement.getAttribute("selected") != null) {
          anySelected = true;
          result.add(name, HtmlUtils.getOptionValue(optionElement));
          if (!multiple) {
            break;
          }
        }
      }
      // Required fields are marked with 'required' attribute (checked on BZ5.0rc2)
      String required = JDOMUtils.getAttributeValue(selectElement, "required", null, false);
      if (!anySelected && options.size() > 0) {
        if (!multiple ) {
          // browsers submit first option by default
          // http://www.w3.org/TR/html4/interact/forms.html#edef-SELECT
          result.add(name, HtmlUtils.getOptionValue(options.get(0)));
        } else if (myIncludeAllSelects || required != null) // Add required attribute even it has not preselected options
          result.add(name, "");
      }
    }
  }

  public MultiMap<String, String> perform(Element form) {
    MultiMap<String, String> result = MultiMap.create();
    findInputs(form, result);
    formSelects(form, result);
    findTextAreas(form, result);
    if (myIncludeSubmit) {
      findSubmit(form, result);
    }
    return result;
  }

  private static void findSubmit(Element form, MultiMap<String, String> result) {
    Element submit = JDOMUtils.searchElement(form, "input", "type", "submit");
    if (submit != null) {
      String name = JDOMUtils.getAttributeValue(submit, "name", "", false).trim();
      if (name.length() > 0) {
        String value = JDOMUtils.getAttributeValue(submit, "value", null, true);
        if (value == null) {
          value = JDOMUtils.getTextTrim(submit);
        }
        result.add(name, value);
      }
    }
  }

  private static void findTextAreas(Element form, MultiMap<String, String> result) {
    List<Element> list = JDOMUtils.searchElements(form, "textarea");
    for (Element textArea : list) {
      String name = JDOMUtils.getAttributeValue(textArea, "name", "", false);
      if (name.length() == 0)
        continue;
      result.add(name, JDOMUtils.getText(textArea));
    }
  }

  private static void findInputs(Element rootElement, MultiMap<String, String> result) {
    final List<Element> elements = JDOMUtils.searchElements(rootElement, "input");
    for (int i = 0; i < elements.size(); i++) {
      Element element = elements.get(i);
      String type = JDOMUtils.getAttributeValue(element, "type", "text", false);
      if (isTypeIgnored(type))
        continue;
      if (isNotChecked(element, type))
        continue;
      final String name = JDOMUtils.getAttributeValue(element, "name", null, true);
      if (name != null) {
        String value = JDOMUtils.getAttributeValue(element, "value", "", true);
        result.add(name, value);
      }
    }
  }

  private static boolean isTypeIgnored(String type) {
    return "reset".equalsIgnoreCase(type) || "image".equalsIgnoreCase(type) || "button".equalsIgnoreCase(type) ||
      "file".equalsIgnoreCase(type) || "submit".equalsIgnoreCase(type);
  }

  private static boolean isNotChecked(Element element, String type) {
    return ("radio".equalsIgnoreCase(type) || "checkbox".equalsIgnoreCase(type)) &&
      JDOMUtils.getAttributeValue(element, "checked", null, false) == null;
  }
}
