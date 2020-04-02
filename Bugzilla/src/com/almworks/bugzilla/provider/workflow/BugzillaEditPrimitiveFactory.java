package com.almworks.bugzilla.provider.workflow;

import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.explorer.workflow.*;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.i18n.Local;
import com.almworks.util.text.NameMnemonic;
import org.jetbrains.annotations.*;

import java.util.List;

public class BugzillaEditPrimitiveFactory implements CustomEditPrimitiveFactory {
  @Override
  public LoadedEditPrimitive<?> createPrimitive(ReadonlyConfiguration attrConfig, String attrName) {
    if (isKnownCustomAttribute(attrName)) {
      boolean editable = false;
      String refParam = attrConfig.getSetting(WorkflowLoadUtils.REFERENCE_PARAM, null);
      if(refParam == null) {
        refParam = attrConfig.getSetting(WorkflowLoadUtils.EDITABLE_REFERENCE_PARAM, null);
        editable = refParam != null;
      }
      String valueParam = attrConfig.getSetting(WorkflowLoadUtils.VALUE, null);
      return createCustomPrimitive(attrName, refParam, editable, valueParam,
        getExclusions(attrConfig), getInclusions(attrConfig));
    }
    return null;
  }

  private static boolean isKnownCustomAttribute(String attrName) {
    return isReporting(attrName) || isProductDependent(attrName) || isResolution(attrName) || isUserField(attrName);
  }

  private static boolean isReporting(String attrName) {
    return BugzillaKeys.product.getName().equalsIgnoreCase(attrName)
        || BugzillaKeys.status.getName().equalsIgnoreCase(attrName);
  }

  private static boolean isProductDependent(String attrName) {
    return BugzillaKeys.component.getName().equalsIgnoreCase(attrName)
        || BugzillaKeys.version.getName().equalsIgnoreCase(attrName)
        || BugzillaKeys.milestone.getName().equalsIgnoreCase(attrName);
  }

  private static boolean isResolution(String attrName) {
    return BugzillaKeys.resolution.getName().equalsIgnoreCase(attrName);
  }

  private static boolean isUserField(String attrName) {
    return BugzillaKeys.assignedTo.getName().equalsIgnoreCase(attrName)
        || BugzillaKeys.qaContact.getName().equalsIgnoreCase(attrName)
        || BugzillaKeys.reporter.getName().equalsIgnoreCase(attrName);
  }

  private static List<String> getExclusions(ReadonlyConfiguration attrConfig) {
    return attrConfig.getAllSettings(WorkflowLoadUtils.REFERENCE_PARAM_EXCLUDE);
  }

  private static List<String> getInclusions(ReadonlyConfiguration attrConfig) {
    return attrConfig.getAllSettings(WorkflowLoadUtils.REFERENCE_PARAM_INCLUDE);
  }

  @Nullable
  private static LoadedEditPrimitive createCustomPrimitive(
    String attrName, String refParam, boolean editable, String valueParam,
    List<String> exclusions, List<String> inclusions)
  {
    if (refParam != null) {
      final NameMnemonic mn = NameMnemonic.parseString(Local.parse(refParam));
      if (isReporting(attrName)) {
        return new EditableReportingEditPrimitive(attrName, mn, exclusions, inclusions);
      } else if (isProductDependent(attrName)) {
        return new ProductDependentEditPrimitive(attrName, mn, exclusions, inclusions);
      } else if (isResolution(attrName)) {
        return new ResolutionEditPrimitive(attrName, mn, exclusions, inclusions);
      } else if (isUserField(attrName)) {
        return new UserEditPrimitive(attrName, mn, editable, exclusions, inclusions);
      }
    } else if (valueParam != null && isReporting(attrName)) {
      return new ValueReportingEditPrimitive(attrName, valueParam);
    }
    return null;
  }
}
