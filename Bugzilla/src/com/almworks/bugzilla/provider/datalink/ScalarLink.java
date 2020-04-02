package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;

public class ScalarLink<T> extends AbstractAttributeLink<T> {
  protected final boolean myReadonly;

  public ScalarLink(DBAttribute<T> attribute, BugzillaAttribute bugzillaAttribute, boolean ignoreEmpty, boolean inPrototype, boolean isReadonly) {
    super(attribute, bugzillaAttribute, ignoreEmpty, inPrototype);
    BugzillaUtil.assertScalarValueType(attribute);
    myReadonly = isReadonly;
  }

  protected T getValueFromRemoteString(String stringValue, BugInfo bugInfo) {
    return getScalarFromRemoteString(stringValue, getAttribute());
  }

  @SuppressWarnings({"unchecked"})
  public static <T> T getScalarFromRemoteString(String stringValue, DBAttribute<T> attribute) {
    if(stringValue == null) {
      return null;
    }
    final Class<T> clazz = attribute.getValueClass();
    if(String.class.equals(clazz)) {
      return (T)stringValue;
    } else if(Integer.class.equals(clazz)) {
      try {
        return (T)Integer.valueOf(stringValue);
      } catch(NumberFormatException e) {
        Log.error(stringValue, e);
      }
    } else if(BigDecimal.class.equals(clazz)) {
      try {
        return (T)new BigDecimal(stringValue);
      } catch(NumberFormatException e) {
        Log.error(stringValue, e);
      }
    } else {
      assert false : stringValue + " " + clazz;
    }
    return null;
  }

  public void updateRevision(PrivateMetadata privateMetadata, ItemVersionCreator bugCreator, BugInfo bugInfo,
    @NotNull BugzillaContext context) {
    if (cannotTell(bugInfo))
      return;

    String stringValue = getRemoteString(bugInfo, context.getPrivateMetadata());
    T value = getValueFromRemoteString(stringValue, bugInfo);
    bugCreator.setValue(getAttribute(), value);
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    String requestedValue = updateInfo.getNewValues().getScalarValue(attribute, null);
    if (requestedValue == null)
      return null;
    String newValue = getRemoteString(newInfo, privateMetadata);
    return detectFailedUpdateValues(requestedValue, newValue) ? null : attribute.getName();
  }

  protected boolean detectFailedUpdateValues(String requestedValue, String newValue) {
    return Util.NN(requestedValue).trim().equals(Util.NN(newValue).trim());
  }

  public String getBCElementParameter(OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection) {
    return getScalarBCEParameter(constraint, getAttribute());
  }

  public static String getScalarBCEParameter(OneFieldConstraint constraint, DBAttribute<?> attribute) {
    if (constraint instanceof FieldSubstringsConstraint) {
      return TextUtil.separate(((FieldSubstringsConstraint) constraint).getSubstrings(), " ");
    } else if (constraint instanceof FieldIntConstraint) {
      final Class<?> valueClass = attribute.getScalarClass();
      if (valueClass != Integer.class && valueClass != BigDecimal.class) {
        assert false : constraint + " " + attribute + " " + valueClass;
        return null;
      }
      BigDecimal value = ((FieldIntConstraint) constraint).getIntValue();
      return value.toString();
    } else {
      assert false : constraint;
      return null;
    }
  }

  public static class Text extends ScalarLink<String> {
    public Text(DBAttribute<String> attribute, BugzillaAttribute bzAttribute, boolean inPrototype, boolean isReadonly) {
      super(attribute, bzAttribute, false, inPrototype, isReadonly);
    }

    @Override
    protected String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
      String str = super.getRemoteString(bugInfo, privateMetadata);
      if (str != null) str = str.trim();
      if (str != null && str.length() == 0) str = null;
      return str;
    }
  }
}