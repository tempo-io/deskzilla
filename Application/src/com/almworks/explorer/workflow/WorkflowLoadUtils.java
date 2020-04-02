package com.almworks.explorer.workflow;

import com.almworks.api.application.qb.FilterNode;
import com.almworks.explorer.qbuilder.filter.FilterGramma;
import com.almworks.util.NonLocalReturn;
import com.almworks.util.config.*;
import com.almworks.util.i18n.Local;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.text.parser.ParseException;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.almworks.api.application.ModelOperation.*;

/**
 * @author dyoma
 */
public class WorkflowLoadUtils {
  public static final String ACTION = "action";
  public static final String WINDOW_ID = "windowId";
  public static final String EDIT_SCRIPT_TAG = "set";
  public static final String SET_ATTRIBUTE_TAG = "attribute";
  public static final String ADD_TEXT_ELEMENT = "askAddTextElement";
  public static final String ADD_TEXT_ELEMENT_CHECKBOX = "checkbox";
  public static final String ADD_TEXT_VALUE = "addTextValue";
  public static final String ADD_NOT_EMPTY_TEXT_ELEMENT_PARAM = "askAddNotEmptyTextElement";
  public static final String EDITABLE_REFERENCE_PARAM = "askEditableReference";
  public static final String REFERENCE_PARAM = "askReference";
  public static final String STRING_PARAM = "askString";
  public static final String VALUE = "value";
  public static final String REFERENCE_PARAM_EXCLUDE = "exclude";
  public static final String REFERENCE_PARAM_INCLUDE = "include";

  public static FilterNode readFilter(ReadonlyConfiguration transitionConfig, Map<String, FilterNode> states) throws ConfigurationException
  {
    FilterNode filter;
    String stateName = transitionConfig.getSetting(WorkflowComponentImpl.CONDITION, null);
    if (stateName != null) {
      filter = states.get(stateName);
      if (filter == null)
        throw new ConfigurationException("undeclared condition: " + stateName);
    } else {
      String filterFormula = transitionConfig.getSetting(WorkflowComponentImpl.FILTER, null);
      if (filterFormula == null) {
        throw new ReadonlyConfiguration.NoSettingException(WorkflowComponentImpl.CONDITION + " or " + WorkflowComponentImpl.FILTER);
      }
      try {
        filter = FilterGramma.parse(filterFormula);
      } catch (ParseException e) {
        throw new ConfigurationException("Filter syntax error: " + e.getMessage() + ". " + filterFormula);
      }
    }
    return filter;
  }

  public static Map<String, FilterNode> loadStates(ReadonlyConfiguration config) throws ConfigurationException {
    List<? extends ReadonlyConfiguration> states = config.getAllSubsets(WorkflowComponentImpl.CONDITION);
    Map<String, FilterNode> result = Collections15.hashMap();
    for (ReadonlyConfiguration stateConfig : states) {
      String name;
      String filter;
      name = stateConfig.getMandatorySetting(WorkflowComponentImpl.NAME);
      filter = stateConfig.getMandatorySetting(WorkflowComponentImpl.FILTER);
      FilterNode constraint;
      try {
        constraint = FilterGramma.parse(filter);
      } catch (ParseException e) {
        throw new ConfigurationException("State '" + name + "' syntax error (" + filter + ")", e);
      }
      result.put(name, constraint);
    }
    return result;
  }

  public static List<WorkflowTransition> loadTransitions(
    ReadonlyConfiguration config, Map<String, FilterNode> states, CustomEditPrimitiveFactory customFactory)
  {
    List<? extends ReadonlyConfiguration> transitions = config.getAllSubsets(ACTION);
    List<WorkflowTransition> result = Collections15.arrayList();
    for (ReadonlyConfiguration transitionConfig : transitions) {
      String name;
      try {
        name = transitionConfig.getMandatorySetting(WorkflowComponentImpl.NAME);
      } catch (ReadonlyConfiguration.NoSettingException e) {
        Log.warn(e);
        continue;
      }
      FilterNode filter;
      List<LoadedEditPrimitive> script;
      Icon icon;
      String windowId;
      try {
        script = loadScript(transitionConfig, customFactory);
        icon = ConfigAccessors.icon(transitionConfig);
        windowId = transitionConfig.getSetting(WINDOW_ID, null);
        if (windowId == null)
          windowId = getDefaultWindowId(name);
        filter = readFilter(transitionConfig, states);
      } catch (ConfigurationException e) {
        Log.warn(name, e);
        continue;
      }
      result.add(new WorkflowTransition(NameMnemonic.parseString(name + "\u2026"), script, icon, windowId, filter));
    }
    return result;
  }

  static String getDefaultWindowId(String actionName) {
    StringBuffer result = new StringBuffer(actionName.length());
    for (int i = 0; i < actionName.length(); i++) {
      char c = actionName.charAt(i);
      if (Character.isLetter(c))
        result.append(Character.toLowerCase(c));
    }
    if (result.length() == 0)
      result.append("workflowAction");
    return result.toString();
  }

  private static List<LoadedEditPrimitive> loadScript(
    ReadonlyConfiguration config, @Nullable CustomEditPrimitiveFactory customFactory)
  {
    List<LoadedEditPrimitive> result = Collections15.arrayList();
    for (ReadonlyConfiguration attrConfig : config.getSubset(EDIT_SCRIPT_TAG).getAllSubsets(SET_ATTRIBUTE_TAG)) {
      String attrName;
      try {
        attrName = attrConfig.getMandatorySetting(WorkflowComponentImpl.NAME);
      } catch (ReadonlyConfiguration.NoSettingException e) {
        Log.error(e);
        assert false : e.getMessage();
        continue;
      }
      LoadedEditPrimitive<?> primitive = null;
      if (customFactory != null) {
        primitive = customFactory.createPrimitive(attrConfig, attrName);
      }
      if (primitive == null) {
        primitive = createDefaultPrimitive(attrConfig, attrName);
      }
      if (primitive != null) {
        result.add(primitive);
      }
    }
    return result;
  }

  @Nullable
  private static LoadedEditPrimitive<?> createDefaultPrimitive(ReadonlyConfiguration attrConfig, String attrName) {
    try {
      createValue(attrConfig, attrName);
      createText(attrConfig, attrName);
      createNotEmptyText(attrConfig, attrName);
      createReference(attrConfig, attrName);
      createEditableReference(attrConfig, attrName);
      createString(attrConfig, attrName);
    } catch(NonLocalReturn nlr) {
      return nlr.getValue(LoadedEditPrimitive.class);
    }

    Log.error("No known modification for " + attrName);
    assert false : attrName;
    return null;
  }

  private static void createValue(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String stringValue = attrConfig.getSetting(VALUE, null);
    if(stringValue != null) {
      final SetConstant primitive = new SetConstant(attrName, stringValue, SET_STRING_VALUE);
      throw new NonLocalReturn(primitive);
    }
  }

  private static void createText(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String addStringParam = attrConfig.getSetting(ADD_TEXT_ELEMENT, null);
    if(addStringParam != null) {
      final NameMnemonic nm = NameMnemonic.parseString(localize(addStringParam));
      final String checkbox = attrConfig.getSetting(ADD_TEXT_ELEMENT_CHECKBOX, null);
      final AddTextAreaParam<? extends Object> primitive = checkbox != null ?
        AddTextAreaParam.textEditorWithCheckbox(attrName, ADD_STRING_BOOL_VALUE, nm, checkbox, false) :
        AddTextAreaParam.textEditor(attrName, ADD_STRING_VALUE, nm);
      throw new NonLocalReturn(primitive);
    }
  }

  private static void createNotEmptyText(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String addNotEmptyString = attrConfig.getSetting(ADD_NOT_EMPTY_TEXT_ELEMENT_PARAM, null);
    if(addNotEmptyString != null) {
      final NameMnemonic nm = NameMnemonic.parseString(localize(addNotEmptyString));
      final String checkbox = attrConfig.getSetting(ADD_TEXT_ELEMENT_CHECKBOX, null);
      final AddTextAreaParam<? extends Object> primitive = checkbox != null ?
        AddTextAreaParam.textEditorWithCheckbox(attrName, ADD_NOT_EMPTY_STRING_BOOL_VALUE, nm, checkbox, false) :
        AddTextAreaParam.textEditor(attrName, ADD_NOT_EMPTY_STRING_VALUE, nm);
      throw new NonLocalReturn(primitive);
    }
  }

  private static void createReference(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String refParam = attrConfig.getSetting(REFERENCE_PARAM, null);
    if(refParam != null) {
      final List<String> exclusions = attrConfig.getAllSettings(REFERENCE_PARAM_EXCLUDE);
      final List<String> inclusions = attrConfig.getAllSettings(REFERENCE_PARAM_INCLUDE);
      final NameMnemonic mn = NameMnemonic.parseString(localize(refParam));
      final SetComboBoxParam primitive = new SetComboBoxParam(attrName, SET_ITEM_KEY, mn, false, exclusions, inclusions);
      throw new NonLocalReturn(primitive);
    }
  }

  private static void createEditableReference(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String editableRefParam = attrConfig.getSetting(EDITABLE_REFERENCE_PARAM, null);
    if(editableRefParam != null) {
      final NameMnemonic mn = NameMnemonic.parseString(localize(editableRefParam));
      final SetComboBoxParam primitive = new SetComboBoxParam(attrName, SET_ITEM_KEY, mn, true);
      throw new NonLocalReturn(primitive);
    }
  }

  private static void createString(ReadonlyConfiguration attrConfig, String attrName) throws NonLocalReturn {
    final String stringParam = attrConfig.getSetting(STRING_PARAM, null);
    if(stringParam != null) {
      final NameMnemonic mn = NameMnemonic.parseString(localize(stringParam));
      final SetTextFieldParam primitive = new SetTextFieldParam(attrName, SET_STRING_VALUE, mn);
      throw new NonLocalReturn(primitive);
    }
  }

  private static String localize(String s) {
    return Local.parse(s);
  }
}
