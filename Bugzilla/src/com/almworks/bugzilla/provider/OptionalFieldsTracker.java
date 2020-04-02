package com.almworks.bugzilla.provider;

import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.AbstractAttributeLink;
import com.almworks.bugzilla.provider.datalink.BugzillaAttributeLink;
import com.almworks.util.collections.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.commons.Procedure;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.hashMap;

/**
 * Tracks which optional fields are used.
 */
public class OptionalFieldsTracker {
  private final static String STORE = "OFT";

  private final Store myStore;

  // todo - for some reason, EnumSet.noneOf() throws CCE (class not an enum) after obfuscation
//  private final EnumSet<OptionalField> myUnused = EnumSet.noneOf(OptionalField.class);
  private final Set<OptionalField> myUnused = Collections15.hashSet();
  private final SimpleModifiable myModifiable = new SimpleModifiable();
  
  private final static Map<BugzillaAttribute, OptionalField> fromAttribute = hashMap();
  static {
    for (OptionalField f : OptionalField.values()) {
      OptionalField prevAlias = fromAttribute.put(f.myAttribute, f);
      assert prevAlias == null;
    }
  }

  public static Set<BugzillaAttribute> getTrackedFields() {
    return fromAttribute.keySet();
  }

  public OptionalFieldsTracker(Store store) {
    myStore = store;
  }

  public void start() {
    load();
  }

  private void load() {
    List<OptionalField> unused = Functional.filterArray(OptionalField.getLoader(myStore), OptionalField.values());
    synchronized (myUnused) {
      myUnused.clear();
      myUnused.addAll(unused);
    }
    myModifiable.fireChanged();
  }

  private void save() {
    Set<OptionalField> unused;
    synchronized (myUnused) {
      unused = Collections15.hashSet(myUnused);
    }
    Containers.apply(OptionalField.getSaver(myStore, unused), OptionalField.values());
  }

  /**
   * @return true iff changed
   */
  public boolean reportUnused(BugzillaAttribute attribute, boolean unused) {
    OptionalField optionalField = getOptionalField(attribute);
    if (optionalField == null) return false;
    boolean changed;
    synchronized (myUnused) {
      changed = unused ? myUnused.add(optionalField) : myUnused.remove(optionalField);
    }
    if (changed) {
      save();
      changeInPrototype(attribute, unused);
      myModifiable.fireChanged();
    }
    return changed;
  }

  private void changeInPrototype(BugzillaAttribute attribute, boolean unused) {
    BugzillaAttributeLink link = CommonMetadata.ATTR_TO_LINK.get(attribute);
    if (link instanceof AbstractAttributeLink) {
      ((AbstractAttributeLink)link).setInPrototype(!unused);
    }
  }

  public boolean isUnused(BugzillaAttribute attribute) {
    OptionalField optionalField = getOptionalField(attribute);
    if (optionalField == null) return false;
    synchronized (myUnused) {
      return myUnused.contains(optionalField);
    }
  }

  @Nullable
  private OptionalField getOptionalField(BugzillaAttribute attribute) {
    OptionalField optionalField = fromAttribute.get(attribute);
    if (optionalField == null) {
      if (attribute.isOptional()) {
        assert false;
      }
      Log.warn("OFT: unknown optional field " + attribute);
    }
    return optionalField;
  }

  public SimpleModifiable getModifiable() {
    return myModifiable;
  }

  public static enum OptionalField {
    TARGET_MILESTONE_UNUSED("TMU", BugzillaAttribute.TARGET_MILESTONE),
    QA_CONTACT_UNUSED("QCU", BugzillaAttribute.QA_CONTACT),
    STATUS_WHITEBOARD_UNUSED("SWU", BugzillaAttribute.STATUS_WHITEBOARD),
    ALIAS_UNUSED("AU", BugzillaAttribute.ALIAS),
    SEE_ALSO_UNUSED("SAU", BugzillaAttribute.SEE_ALSO),
    VOTES_UNUSED("VU", BugzillaAttribute.TOTAL_VOTES);

    private final String myStoreKey;
    private final BugzillaAttribute myAttribute;

    OptionalField(String storeKey, BugzillaAttribute attribute) {
      assert attribute.isOptional();
      myStoreKey = storeKey;
      myAttribute = attribute;
    }

    /**
     * Condition returns true iff store contains information that the optional field is unused.
     */
    public static Condition<OptionalField> getLoader(final Store store) {
      return new Condition<OptionalField>() {
        @Override
        public boolean isAccepted(OptionalField value) {
          return StoreUtils.restoreBoolean(store, STORE, value.myStoreKey);
        }
      };
    }

    public static Procedure<OptionalField> getSaver(final Store store, final Set<OptionalField> unused) {
      return new Procedure<OptionalField>() {
        @Override
        public void invoke(OptionalField arg) {
          StoreUtils.storeBoolean(store, STORE, arg.myStoreKey, unused.contains(arg));
        }
      };
    }
  }
}
