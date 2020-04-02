package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBItemType;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.DatabaseUtil;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.CanvasRenderer;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.List;

public class SingleEnumAttribute {
  public static final SingleEnumAttribute OS = simpleOwnEnum(BugzillaAttribute.OPERATING_SYSTEM, "operatingSystem", "operating_system");
  public static final SingleEnumAttribute PLATFORM = simpleOwnEnum(BugzillaAttribute.PLATFORM, "platform", "platform");
  public static final SingleEnumAttribute PRIORITY = simpleOwnEnum(BugzillaAttribute.PRIORITY, "priority", "priority");
  public static final SingleEnumAttribute SEVERITY = simpleOwnEnum(BugzillaAttribute.SEVERITY, "severity", "severity");
  public static final SingleEnumAttribute STATUS = simpleOwnEnum(BugzillaAttribute.STATUS, "status", "status");
  public static final SingleEnumAttribute CLASSIFICATION = ownEnum(
    BugzillaAttribute.CLASSIFICATION, "classification", "classification", false, true, null);
  public static final SingleEnumAttribute RESOLUTION = ownEnum(
    BugzillaAttribute.RESOLUTION, "resolution", "resolution", false, false, BugzillaAttribute.RESOLUTION.getEmptyValueName());
  public static final SingleEnumAttribute PRODUCT = productDependent(Product.ENUM_PRODUCTS, BugzillaAttribute.PRODUCT, "product");
  public static final SingleEnumAttribute VERSION = productDependent(BugzillaAttribute.VERSION, "version");
  public static final SingleEnumAttribute COMPONENT = productDependent(BugzillaAttribute.COMPONENT, "component");
  public static final SingleEnumAttribute TARGET_MILESTONE = productDependent(BugzillaAttribute.TARGET_MILESTONE, "milestone");
  public static final SingleEnumAttribute ASSIGNED_TO = singleUser(BugzillaAttribute.ASSIGNED_TO, "assignedTo", "assigned_to", false,
    false, false);
  public static final SingleEnumAttribute QA_CONTACT = singleUser(BugzillaAttribute.QA_CONTACT, "qaContact", "qa_contact", false, false, false);
  public static final SingleEnumAttribute REPORTER = singleUser(BugzillaAttribute.REPORTER, "reporter", "reporter", false, true, true);
  public static final SingleEnumAttribute MODIFICATION_AUTHOR
    = singleUser(BugzillaAttribute.MODIFICATION_AUTHOR, "modificationAuthor", "modification_author", true, false, true);

  private static SingleEnumAttribute singleUser(
    BugzillaAttribute bzAttr, String bugAttrId, String bugAttrName,
    boolean ignoreEmpty, boolean prototypeCurrentUser, boolean readonly)
  {
    DBAttribute<Long> bugAttr = createBugAttribute(bugAttrId, bugAttrName, !readonly);
    return new SingleEnumAttribute(
      User.ENUM_USERS, bzAttr, bugAttr,
      new UserReferenceLink(bugAttr, bzAttr, ignoreEmpty, prototypeCurrentUser, readonly),
      !readonly, EnumNarrower.DEFAULT, User.RENDERER);
  }


  private final EnumType myEnumType;
  private final BugzillaAttribute myBugzillaAttribute;
  private final DBAttribute<Long> myBugAttribute;
  private final SingleReferenceLink<String> myRefLink;
  private final boolean myEditable;
  private final EnumNarrower myNarrower;
  private final CanvasRenderer<ItemKey> myValueRenderer;

  public SingleEnumAttribute(EnumType enumType, BugzillaAttribute bzAttribute, DBAttribute<Long> bugAttribute,
    SingleReferenceLink<String> link, boolean editable, EnumNarrower narrower, CanvasRenderer<ItemKey> valueRenderer) {
    myValueRenderer = valueRenderer;
    assert enumType != null;
    assert bzAttribute != null;
    assert bugAttribute != null;
    assert link != null;
    assert narrower != null;
    myEnumType = enumType;
    myBugzillaAttribute = bzAttribute;
    myBugAttribute = bugAttribute;
    myRefLink = link;
    myEditable = editable;
    myNarrower = narrower;
  }

  private static SingleEnumAttribute simpleOwnEnum(BugzillaAttribute bzAttribute, String attrId, String attrName) {
    return ownEnum(bzAttribute, attrId, attrName, true, true, null);
  }

  private static SingleEnumAttribute ownEnum(BugzillaAttribute bzAttribute, String attrId, String attrName, boolean inProto,
    boolean checkUpdate, String defaultValue) {
    EnumType enumType = EnumType.create(BugzillaProvider.NS.subNs(attrId), null, null, bzAttribute.getName(), defaultValue);
    DBAttribute<Long> bugAttr = createBugAttribute(attrId, attrName, true);
    enumType.getType().initialize(EnumType.BUG_ATTR, bugAttr);
    DefaultReferenceLink link = enumType.createDefaultRefLink(bugAttr, bzAttribute, inProto, checkUpdate);
    return new SingleEnumAttribute(enumType, bzAttribute, bugAttr, link, true, EnumNarrower.DEFAULT, null);
  }

  private static SingleEnumAttribute productDependent(BugzillaAttribute bzAttribute, String subNS) {
    EnumType enumType = EnumType.create(BugzillaProvider.NS.subNs(subNS), null, null, bzAttribute.getName(), null);
    return productDependent(enumType, bzAttribute, subNS);
  }

  public static SingleEnumAttribute productDependent(EnumType enumType, BugzillaAttribute bzAttribute, String subNS) {
    DBAttribute<Long> bugAttribute = createBugAttribute(subNS, subNS, true);
    enumType.getType().initialize(EnumType.BUG_ATTR, bugAttribute);
    DefaultReferenceLink link = enumType.createDefaultRefLink(bugAttribute, bzAttribute, true, true);
    EnumNarrower narrower = ProductDependentNarrower.create(bugAttribute);
    return new SingleEnumAttribute(enumType, bzAttribute, bugAttribute, link, true, narrower, null);
  }

  private static DBAttribute<Long> createBugAttribute(String attrId, String attrName, boolean shadowable) {
    return Bug.BUG_NS.link(attrId, attrName, shadowable);
  }

  public final DBItemType getType() {
    return myEnumType.getType();
  }

  public final ResolvedFactory<ResolvedItem> getFactory() {
    return myEnumType.getFactory();
  }
  public final EnumType getEnumType() {
    return myEnumType;
  }

  public final DBAttribute<Long> getBugAttribute() {
    return myBugAttribute;
  }

  public final BugzillaAttribute getBugzillaAttribute() {
    return myBugzillaAttribute;
  }

  public final String getDisplayableFieldName() {
    return BugzillaUtil.getDisplayableFieldName(myBugzillaAttribute);
  }

  public final boolean isEditable() {
    return myEditable;
  }

  @Nullable
  public ResolvedItem resolveItemKey(CommonMetadata md, ItemHypercube cube, ItemKey newValue) {
    List<ResolvedItem> target = Collections15.arrayList();
    ItemKeyModelCollector<ResolvedItem> collector = myEnumType.getKeyCollector(md);
    BaseEnumConstraintDescriptor.resolveItemId(newValue.getId(), cube, target, myNarrower, collector);
    return target.size() == 1 ? target.get(0) : null;
  }

  public ModelMergePolicy getMergePolicy() {
    return isEditable() ? ModelMergePolicy.MANUAL : ModelMergePolicy.COPY_VALUES;
  }

  public void registerEnum(CommonMetadata.Registrator registrator) {
    BaseEnumConstraintDescriptor descriptor = myEnumType.singleValueDescriptor(
      registrator.getMd(), myBugAttribute, myNarrower, getDisplayableFieldName(), myValueRenderer);
    registrator.register(myBugzillaAttribute, myBugAttribute, descriptor);
  }

  public void registerRefLink(CommonMetadata.LinksCollector collector) {
    collector.link(myRefLink);
  }

  public SingleReferenceLink<String> getRefLink() {
    return myRefLink;
  }

  public String getLocalStringForBug(ItemVersion bug) throws ReferenceLink.BadReferent {
    final Long referent = bug.getValue(getBugAttribute());
    if(referent == null || referent <= 0) return null;
    final ItemVersion value = bug.forItem(referent);
    checkReferent(value, bug);
    String str = myEnumType.getStringId(value);
    if(str == null) throw new ReferenceLink.BadReferent(getBugzillaAttribute(), "bad referent " + referent + ": no key value");
    return str;
  }

  private void checkReferent(ItemVersion referent, ItemVersion bug) throws ReferenceLink.BadReferent {
    if (referent.isInvisible()) {
      throw new ReferenceLink.BadReferent(getBugzillaAttribute(), "deleted referent");
    }

    if(!DatabaseUtil.itemsEqual(referent.getReader().findMaterialized(getType()), referent.getValue(DBAttribute.TYPE))) {
      throw new ReferenceLink.BadReferent(getBugzillaAttribute(),
        "referent type is not as expected (was there an upgrade recently?) [" + this + "]");
    }

    if (!DatabaseUtil.itemsEqual(bug.getValue(SyncAttributes.CONNECTION), referent.getValue(SyncAttributes.CONNECTION))) {
      throw new ReferenceLink.BadReferent(getBugzillaAttribute(), "referent points to a value from another provider [" + this + "]");
    }
  }

  public VariantsModelFactory extractVariants(LoadedItemServices itemServices) {
    BugzillaConnection connection = itemServices.getConnection(BugzillaConnection.class);
    if (connection == null) {
      Log.error("Missing Bugzilla connection");
      return null;
    }
    return myEnumType.getVariantsFactory(connection.getCommonMD(), itemServices.getConnectionCube(), myNarrower);
  }

  private static class ProductDependentNarrower extends Convertor<Connection, EnumNarrower> {
    private final DBAttribute<Long> myAttribute;

    public ProductDependentNarrower(DBAttribute<Long> attribute) {
      myAttribute = attribute;
    }

    public static EnumNarrower create(DBAttribute<Long> attribute) {
      return new EnumNarrower.AggregatingNarrower(new ProductDependentNarrower(attribute));
    }

    public EnumNarrower convert(final Connection connection) {
      if (connection instanceof BugzillaConnection) {
        final BugzillaConnection bugzillaConnection = ((BugzillaConnection) connection);
        return new EnumNarrower() {
          public AListModel<? extends ResolvedItem> narrowModel(Lifespan life,
            AListModel<? extends ResolvedItem> original, ItemHypercube cube)
          {
            ProductDependenciesTracker tracker = bugzillaConnection.getDependenciesTracker();
            AListModel<? extends ResolvedItem> narrowed = tracker.narrowEnums(life, myAttribute, original, cube);
            if (narrowed == null) {
              return EnumNarrower.DEFAULT.narrowModel(life, original, cube);
            } else {
              return narrowed;
            }
          }

          public List<ResolvedItem> narrowList(List<ResolvedItem> values, ItemHypercube cube) {
            ProductDependenciesTracker tracker = bugzillaConnection.getDependenciesTracker();
            List<ResolvedItem> narrowed = tracker.narrowList(myAttribute, values, cube);
            return narrowed != null ? narrowed : values;
          }
        };
      } else {
        return EnumNarrower.DEFAULT;
      }
    }
  }
}
