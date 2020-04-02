package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.util.Pair;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

public abstract class AbstractAttributeLink<T> implements BugzillaAttributeLink<T> {
  protected final DBAttribute<T> myAttribute;
  protected final BugzillaAttribute myBugzillaAttribute;
  protected final boolean myIgnoreEmpty;
  private final SynchronizedBoolean myInPrototype;

  public AbstractAttributeLink(
    DBAttribute<T> attribute, BugzillaAttribute bugzillaAttribute,
    boolean ignoreEmpty, boolean inPrototype)
  {
    assert bugzillaAttribute != null;
    myAttribute = attribute;
    myBugzillaAttribute = bugzillaAttribute;
    myIgnoreEmpty = ignoreEmpty;
    myInPrototype = new SynchronizedBoolean(inPrototype);
  }

  public final BugzillaAttribute getBugzillaAttribute() {
    return myBugzillaAttribute;
  }

  public final DBAttribute<T> getWorkspaceAttribute() {
    return getAttribute();
  }

  @Nullable
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    return buildBooleanChartElement(
      constraint, negated, getBCElementParameter(constraint, reader, connection),
      getBugzillaAttribute());
  }

  public static BooleanChart.Element buildBooleanChartElement(OneFieldConstraint constraint, boolean negated,
    String parameter, BugzillaAttribute bzAttribute)
  {
    BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
    if (type == null)
      return null;
    String value = parameter;
    if (value == null)
      return null;
    Pair<String, BooleanChartElementType> fixedPair = LinkUtil.fixEmptyBooleanChartCondition(value, type);
    if (fixedPair != null) {
      value = fixedPair.getFirst();
      type = fixedPair.getSecond();
      if (type == null || value == null)
        return null;
    }
    return BooleanChart.createElement(bzAttribute, type, value);
  }

  @Nullable
  protected abstract String getBCElementParameter(
    OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection);

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    DBAttribute<T> attribute = getWorkspaceAttribute();
    BugzillaAttribute bugzillaAttribute = getBugzillaAttribute();

    T trunkValue = bug.getNewerValue(attribute);
    T serverValue = bug.getElderValue(attribute);
    if (serverValue != null) {
      putValue(info.getPrevValues(), bugzillaAttribute, serverValue, false, bug.getElderVersion());
    }
    if (!Util.equals(trunkValue, serverValue)) {
      putValue(info.getNewValues(), bugzillaAttribute, trunkValue, true, bug.getElderVersion());
    }
  }

  private void putValue(
    BugzillaValues values, BugzillaAttribute bugzillaAttribute, T value, boolean isNew, ItemVersion lastServerVersion)
  {
    String valueString = value == null ? null : createRemoteString(value, lastServerVersion);
    if (valueString == null) {
      if (value != null) {
        if (isNew) {
          Log.warn(this + ": cannot convert value to string (" + value + ")");
        }
        return;
      } else {
        // value == null: clear
        valueString = "";
      }
    }

    values.reput(bugzillaAttribute, valueString);
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    Object requestedValue = updateInfo.getNewValues().getValue(attribute);
    if (requestedValue == null)
      return null;
    Object newValue = newInfo.getValues().getValue(attribute);
    return Util.NN(requestedValue, "").equals(Util.NN(newValue, "")) ? null : attribute.getName();
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
    prototype.setValue(myAttribute, myInPrototype.get() ? getPrototypeValue(pm, prototype.getReader()) : null);
  }

  protected T getPrototypeValue(PrivateMetadata privateMetadata, DBReader reader) {
    return null;
  }

  // todo: this is probably trivial enough to be done by the generic automerge
  protected boolean resolveToEqualValue(AutoMergeData data) {
    final DBAttribute<T> attribute = getAttribute();
    if(data.getUnresolved().contains(attribute)) {
      final T server = data.getServer().getNewerValue(attribute);
      final T local = data.getLocal().getNewerValue(attribute);
      if(Util.equals(server, local)) {
        data.discardEdit(attribute);
        return true;
      } else if(String.class.equals(attribute.getScalarClass())) {
        if((local == null || String.valueOf(local).isEmpty()) && (server == null || String.valueOf(server).isEmpty())) {
          data.discardEdit(attribute);
          return true;
        }
      }
      return false;
    }
    return true;
  }

  //  public void onCloseLocalChain(RevisionCreator revisionCreator, Transaction transaction) {
//  }
//

  @Nullable
  protected String createRemoteString(T value, ItemVersion lastServerVersion) {
    // todo: Some fancier coercion like Value did?
    // NB: Dates, references and booleans are handled by subclasses
    return value == null ? null : String.valueOf(value);
  }

  protected boolean cannotTell(BugInfo bugInfo) {
    return !bugInfo.getValues().contains(myBugzillaAttribute) && myIgnoreEmpty;
  }

  public String toString() {
    return "link[" + myAttribute + "]";
  }

  protected String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
    return bugInfo.getValues().getScalarValue(myBugzillaAttribute, null);
  }

  protected final DBAttribute<T> getAttribute() {
    return myAttribute;
  }

  public final void setInPrototype(boolean inPrototype) {
    myInPrototype.set(inPrototype);
  }
}
