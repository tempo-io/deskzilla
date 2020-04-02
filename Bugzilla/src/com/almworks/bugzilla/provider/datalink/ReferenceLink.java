package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public abstract class ReferenceLink<T, K> extends AbstractAttributeLink<T> {
  protected final String myTypeID;
  protected final DBItemType myReferentType;
  protected final DBAttribute<K> myReferentUniqueKey;
  protected final DBAttribute<K> myReferentVisualKey;
  protected final K myDefaultValue;

  public ReferenceLink(DBAttribute<T> attribute, BugzillaAttribute bugzillaAttribute,
    DBItemType referentType, DBAttribute<K> referentUniqueKey, DBAttribute<K> referentVisualKey,
    boolean ignoreEmpty, K defaultValue, boolean inPrototype)
  {
    super(attribute, bugzillaAttribute, ignoreEmpty, inPrototype);
    assert referentType != null;
    assert referentUniqueKey != null;
    assert referentVisualKey != null;
    myReferentType = referentType;
    myReferentUniqueKey = referentUniqueKey;
    myReferentVisualKey = referentVisualKey;
    myTypeID = "-";
    myDefaultValue = defaultValue;
  }

  protected boolean areKeyValuesEqual(String newValue, String currentValue) {
    return Util.equals(newValue, currentValue);
  }

  public static final class BadReferent extends Exception {
    private final BugzillaAttribute myAttribute;

    public BadReferent(BugzillaAttribute attribute, String message) {
      super(message);
      myAttribute = attribute;
    }

    public BugzillaAttribute getAttribute() {
      return myAttribute;
    }
  }

  public final long getOrCreateReferent(final PrivateMetadata privateMetadata, final K keyValue, DBDrain drain) {
    return createProxy(privateMetadata, keyValue).findOrCreate(drain);
  }

  public ItemProxy createProxy(final PrivateMetadata pm, final K keyValue) {
    return new EnumItemProxy<K>(pm, keyValue, this);
  }

  public final DBAttribute<K> getReferentUniqueKey() {
    return myReferentUniqueKey;
  }

  public final DBAttribute<K> getReferentVisualKey() {
    return myReferentVisualKey;
  }

  public final DBItemType getReferentType() {
    return myReferentType;
  }

  public DBFilter getReferentsView(PrivateMetadata pm) {
    final BoolExpr<DP> expr = BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, getReferentType()),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, pm.thisConnection));
    return pm.getActor(Database.ROLE).filter(expr);
  }

  public String toString() {
    return "reflink[" + getAttribute() + "]";
  }

  public String getRemoteString(BugInfo bugInfo, PrivateMetadata privateMetadata) {
    final String defValue = toString(myDefaultValue);
    final String value = bugInfo.getValues().getScalarValue(myBugzillaAttribute, defValue);
    if(value != null && value.isEmpty()) {
      return defValue;
    }
    return value;
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    BugzillaAttribute attribute = getBugzillaAttribute();
    Object requestedValue = updateInfo.getNewValues().getValue(attribute);
    if (requestedValue == null)
      return null;
    Object newValue = newInfo.getValues().getValue(attribute);

    Object requested = Util.NN(requestedValue, "");
    Object received = Util.NN(newValue, "");
    if (myDefaultValue != null) {
      if (myDefaultValue.equals(requested))
        requested = "";
      if (myDefaultValue.equals(received))
        received = "";
    }
    if (requested.equals(received))
      return null;
    if (requested instanceof String && received instanceof String) {
      return detectFailedUpdateString(privateMetadata, attribute, (String) requested, (String) received);
    }
    return attribute.getName();
  }

  protected String detectFailedUpdateString(PrivateMetadata privateMetadata, BugzillaAttribute attribute,
    String requested, String received)
  {
    if (areKeyValuesEqual(requested, received))
      return null;
    else
      return attribute.getName();
  }

  @Nullable
  public String getBCElementParameter(OneFieldConstraint constraint, DBReader reader, BugzillaConnection connection) {
    final FieldEqualsConstraint equals = Constraints.cast(FieldEqualsConstraint.EQUALS_TO, constraint);
    if(equals == null) {
      return null;
    }
    final Long referent = equals.getExpectedValue();
    if(referent == null || referent <= 0) {
      return null;
    }
    try {
      String string = getLocalString(SyncUtils.readTrunk(reader, referent), connection);
      if(string != null && string.equalsIgnoreCase(toString(myDefaultValue))) {
        // search for default string
        string = "";
      }
      return string;
    } catch (BadReferent e) {
      Log.warn(e);
      return null;
    }
  }

  @Nullable
  public String getLocalString(ItemVersion referent, BugzillaConnection connection) throws BadReferent {
    final K currentValue = referent.getValue(getReferentUniqueKey());
    if(currentValue == null) {
      throw new BadReferent(getBugzillaAttribute(), "bad referent " + referent + ": no key value");
    }
    return toString(currentValue);
  }

  public String toString(K k) {
    return (String)k;
  }

  public K fromString(String s) {
    //noinspection unchecked
    return (K)s;
  }

  protected static class EnumItemProxy<K> implements ItemProxy {
    private final PrivateMetadata myPm;
    private final K myKeyValue;
    private final ReferenceLink<?, K> myLink;

    public EnumItemProxy(PrivateMetadata pm, K keyValue, ReferenceLink<?, K> link) {
      myPm = pm;
      myKeyValue = keyValue;
      myLink = link;
    }

    public K getKeyValue() {
      return myKeyValue;
    }

    @Override
    public long findOrCreate(DBDrain drain) {
      long item = findItem(drain.getReader());
      if (item > 0) {
        drain.changeItem(item).setAlive();
        return item;
      }
      return createItem(drain).getItem();
    }

    protected ItemVersionCreator createItem(DBDrain drain) {
      final ItemVersionCreator creator = drain.createItem();
      assert myKeyValue != null;
      creator.setValue(DBAttribute.TYPE, myLink.getReferentType());
      creator.setValue(SyncAttributes.CONNECTION, myPm.thisConnection);
      creator.setValue(myLink.getReferentUniqueKey(), myKeyValue);
      creator.setValue(myLink.getReferentVisualKey(), myKeyValue);
      return creator;
    }

    @Override
    public long findItem(DBReader reader) {
      final DBFilter filter = myLink.getReferentsView(myPm);
      return DatabaseUnwrapper.query(filter, reader).getItemByKey(myLink.getReferentUniqueKey(), myKeyValue);
    }
  }
}
