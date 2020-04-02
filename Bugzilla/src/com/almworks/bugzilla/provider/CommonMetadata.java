package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.*;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.flags2.UIFlagData;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.bugzilla.provider.datalink.schema.attachments.Attachment;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.datalink.schema.comments.Comment;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.bugzilla.provider.meta.BugzillaMetaInfoAspect;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.util.DBNamespace;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.Factory;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.i18n.Local;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.properties.Role;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.almworks.bugzilla.provider.BugzillaProvider.NS;

public final class CommonMetadata {
  public static final Role<CommonMetadata> ROLE = Role.role("commonMetadata");

  public static final Iterable<DataLink> ATTRIBUTE_LINKS;
  public static final Map<BugzillaAttribute, BugzillaAttributeLink> ATTR_TO_LINK;

  private static final OrderListModel<ConstraintDescriptor> myDescriptorsModel = OrderListModel.create();

  public static final DBNamespace CONNECTION_NS = NS.subNs("connection");
  public static final DBItemType typeConnection = CONNECTION_NS.type();
  public static final DBAttribute<String> attrConnectionID = CONNECTION_NS.string("id", "ID", false);
  public static final DBAttribute<String> attrBugzillaVerison = CONNECTION_NS.string("bugzillaVersion", "Bugzilla Version", false);

  public static final CommentsLink commentsLink = new CommentsLink();
  public static final AttachmentsLink attachmentsLink = new AttachmentsLink();
  public static final KeywordLink keywordLink = new KeywordLink(Bug.attrKeywords);
  public static final SeeAlsoLink seeAlsoLink = new SeeAlsoLink(Bug.attrSeeAlso);
  public static final GroupsLink groupsLink = new GroupsLink();
  public static final VotesLink votesLink = new VotesLink();
  public static final HoursWorkedLink hoursWorkedLink = new HoursWorkedLink();

  public static final TextResolver bugResolver = new BugResolver();

  private static final Map<BugzillaAttribute, BaseEnumConstraintDescriptor> myEnumDescriptors = Collections15.hashMap();
  private static final Map<DBAttribute, BaseEnumConstraintDescriptor> myEnumDescriptorsByAttribute = Collections15.hashMap();

  private static ComponentContainer ourContainer;

  public static final Map<DBItemType, ItemLink> ITEM_LINKS;
  static {
    HashMap<DBItemType, ItemLink> map = Collections15.hashMap();
    map.put(Flags.KIND_FLAG, Flags.SLAVE_LINK);
    map.put(CommentsLink.typeComment, new Comment());
    map.put(AttachmentsLink.typeAttachment, new Attachment());
    map.put(Bug.typeBug, Bug.ITEM_LINK);
    ITEM_LINKS = Collections.unmodifiableMap(map);
  }

  private static final Set<BugzillaAttribute> UNMAPPED_ATTRIBUTES = Collections.unmodifiableSet(
    Collections15.hashSet(BugzillaAttribute.SECURITY_GROUP, BugzillaAttribute.TOTAL_VOTES,
      BugzillaAttribute.VOTER_LIST, BugzillaAttribute.OUR_VOTES));

  static {
    LinksCollector collector = new LinksCollector();

    Bug.registerLinks(collector);
    collector.link(commentsLink);
    collector.link(keywordLink);
    collector.link(seeAlsoLink);
    collector.link(attachmentsLink);
    collector.link(votesLink);
    collector.link(groupsLink);
    collector.link(CustomField.DATA_LINK);
    collector.link(Flags.DATA_LINK);
    collector.link(hoursWorkedLink);

    ATTR_TO_LINK = Collections.unmodifiableMap(collector.attrToLink);
    ATTRIBUTE_LINKS = Collections.unmodifiableList(collector.links);

    for (BugzillaAttribute bugzillaAttribute : BugzillaAttribute.ALL_ATTRIBUTES.values()) {
      if (!ATTR_TO_LINK.containsKey(bugzillaAttribute)) {
        if (!UNMAPPED_ATTRIBUTES.contains(bugzillaAttribute)) {
          Log.debug("attribute is not mapped: " + bugzillaAttribute);
        }
      }
    }
  }

  private final Configuration myConfig;
  private final BugzillaMetaInfoAspect myMetaInfoAspect;
  private final ComponentContainer myContainer;

  public final UIFlagData myFlags;
  private static final Object SERVICE_STUB = new Object();
  private final ConcurrentHashMap<TypedKey<?>, Object> myMetaServices = new ConcurrentHashMap<TypedKey<?>, Object>();

  public CommonMetadata(ComponentContainer subcontainer, Configuration config) {
    myConfig = config;
    myMetaInfoAspect = new BugzillaMetaInfoAspect(subcontainer, config.getOrCreateSubset("meta"));
    myContainer = subcontainer;
    myFlags = new UIFlagData(this, null);
    synchronized (CommonMetadata.class) {
      assert ourContainer == null;
      ourContainer = subcontainer;
    }
  }

  public static ComponentContainer getContainer() {
    synchronized (CommonMetadata.class) {
      assert ourContainer != null;
      return ourContainer;
    }
  }

  public <T> T getActor(Role<T> role) {
    return myContainer.requireActor(role);
  }

  static void registerConstraintDescriptors(CommonMetadata md) {
    final Registrator registrator = new Registrator(md);
    registrator.registerText(BugzillaAttribute.ALIAS);
    registrator.registerText(BugzillaAttribute.BUG_URL);
    registrator.registerText(BugzillaAttribute.SHORT_DESCRIPTION);
    registrator.registerText(BugzillaAttribute.STATUS_WHITEBOARD);

    registrator.registerBugzillaAttributeInt(BugzillaAttribute.ID);
    registrator.registerNumeric(VotesLink.votes, "Votes");

    registrator.registerBugzillaAttributeInt(BugzillaAttribute.ESTIMATED_TIME);
    registrator.registerNumeric(Bug.attrTotalActualTime, "Hours Worked");
    registrator.registerBugzillaAttributeInt(BugzillaAttribute.REMAINING_TIME);

    // for remote searching needs full download
//    registrator.registerNumeric(VotesLink.votesByUser, "Votes(my)");
//    registrator.registerEnumSet(BugzillaAttribute.VOTER_LIST, VotesLink.votesUserList, "Voters", EnumNarrower.DEFAULT);

    registrator.registerDate(BugzillaAttribute.CREATION_TIMESTAMP, true);
    registrator.registerDate(BugzillaAttribute.MODIFICATION_TIMESTAMP, true);
    registrator.registerDate(BugzillaAttribute.DEADLINE, false);

    SingleEnumAttribute.ASSIGNED_TO.registerEnum(registrator);
    SingleEnumAttribute.REPORTER.registerEnum(registrator);
    SingleEnumAttribute.QA_CONTACT.registerEnum(registrator);
    SingleEnumAttribute.OS.registerEnum(registrator);
    SingleEnumAttribute.PLATFORM.registerEnum(registrator);
    SingleEnumAttribute.PRIORITY.registerEnum(registrator);
    SingleEnumAttribute.RESOLUTION.registerEnum(registrator);
    SingleEnumAttribute.SEVERITY.registerEnum(registrator);
    SingleEnumAttribute.STATUS.registerEnum(registrator);
    MultiEnumAttribute.CC.registerEnum(registrator);
    registrator.registerEnumWithResolver(BugzillaAttribute.KEYWORDS, true, keywordLink);

    SingleEnumAttribute.COMPONENT.registerEnum(registrator);
    SingleEnumAttribute.TARGET_MILESTONE.registerEnum(registrator);
    SingleEnumAttribute.VERSION.registerEnum(registrator);
    SingleEnumAttribute.PRODUCT.registerEnum(registrator);

    registrator.registerComments();

    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myDescriptorsModel.addAll(registrator.getDescriptors());
      }
    });
  }

  @NotNull
  public static AListModel<ConstraintDescriptor> getDescriptorsModel() {
    return myDescriptorsModel;
  }

  public void initMetaInfo() {
    MetaInfo metaInfo = myMetaInfoAspect.buildMetaInfo();
    MetaInfo.REGISTRY.registerMetaInfo(Bug.typeBug, metaInfo);
  }

  public AListModel<TableColumnAccessor<LoadedItem, ?>> getFieldColumns() {
    return myMetaInfoAspect.getFieldColumns();
  }

  public static BaseEnumConstraintDescriptor getEnumDescriptor(BugzillaAttribute attribute) {
    return myEnumDescriptors.get(attribute);
  }

  public static BaseEnumConstraintDescriptor getEnumDescriptor(DBAttribute attribute) {
    return myEnumDescriptorsByAttribute.get(attribute);
  }

  public static DBAttribute getWorkspaceAttribute(BugzillaAttribute bzAttribute) {
    return Bug.getDBAttribute(bzAttribute);
  }

  public static RemoteSearchable findLinkByWorkspaceAttribute(DBAttribute attribute, DBReader reader) {
    return Bug.findSearchByAttribute(attribute, reader);
  }

  public Configuration getEditorAttachmentsConfig() {
    return myConfig.getOrCreateSubset("editIssueAttachments");
  }

  public void start() {
    Database db = myContainer.getActor(Database.ROLE);
    myFlags.start(db);
    User.ENUM_USERS.getKeyCollector(this);
    Product.ENUM_PRODUCTS.getKeyCollector(this);
  }

  public Database getDatabase() {
    return getActor(Database.ROLE);
  }

  @Nullable
  public <T> T getMetaService(TypedKey<T> key) {
    Object obj = myMetaServices.get(key);
    //noinspection unchecked
    return obj == SERVICE_STUB ? null : (T)obj;
  }

  public <T> boolean installService(TypedKey<T> key, T service) {
    if (service == null || service == SERVICE_STUB) return false;
    Object prev = myMetaServices.putIfAbsent(key, service);
    return prev == null;
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  public <T> T installOrGetService(TypedKey<T> key, T service) {
    if (service == null || service == SERVICE_STUB) return getMetaService(key);
    Object prev = myMetaServices.putIfAbsent(key, service);
    if (prev == null) return service;
    else if (prev == SERVICE_STUB) return null;
    else return (T) prev;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getOrCreateService(TypedKey<T> key, Factory<T> factory) {
    while (true) {
      Object obj = myMetaServices.get(key);
      if (obj == SERVICE_STUB) Thread.yield();
      else if (obj != null) return (T) obj;
      else {
        Object prev = myMetaServices.putIfAbsent(key, SERVICE_STUB);
        if (prev != null) continue;
        boolean success = false;
        try {
          T service = factory.create();
          if (service == null || service == SERVICE_STUB) return null;
          if (myMetaServices.replace(key, SERVICE_STUB, service)) {
            success = true;
            return service;
          }
        } finally {
          if (!success) myMetaServices.remove(key, SERVICE_STUB);
        }
      }

    }
  }

  public static class Registrator {
    private final List<ConstraintDescriptor> myDescriptors = Collections15.arrayList();
    private final CommonMetadata myMd;

    public Registrator(CommonMetadata md) {
      myMd = md;
    }

    public CommonMetadata getMd() {
      return myMd;
    }

    public List<ConstraintDescriptor> getDescriptors() {
      return myDescriptors;
    }

    public void registerText(BugzillaAttribute attribute) {
      registerSimpleBugzillaAttribute(attribute, TextAttribute.INSTANCE);
    }

    public void registerBugzillaAttributeInt(BugzillaAttribute attribute) {
      registerSimpleBugzillaAttribute(attribute, NumericAttribute.INSTANCE);
    }

    public void registerNumeric(DBAttribute attr, String displayName) {
      registerSimpleAttribute(NumericAttribute.INSTANCE, attr, displayName);
    }

    public void registerDate(BugzillaAttribute attribute, boolean presumeNewBugsInPresent) {
      DBAttribute wa = getWorkspaceAttribute(attribute);
      String displayableName = BugzillaUtil.getDisplayableFieldName(attribute);
      ConstraintDescriptor descriptor =
        new DateConstraintDescriptor(displayableName, wa, presumeNewBugsInPresent);
      myDescriptors.add(descriptor);
    }

    private void registerSimpleBugzillaAttribute(BugzillaAttribute attribute, AttributeConstraintType type) {
      DBAttribute wa = getWorkspaceAttribute(attribute);
      String displayableName = BugzillaUtil.getDisplayableFieldName(attribute);
      registerSimpleAttribute(type, wa, displayableName);
    }

    private void registerSimpleAttribute(AttributeConstraintType type, DBAttribute wa, String displayableName) {
      ConstraintDescriptor descriptor = AttributeConstraintDescriptor.constant(type, displayableName, wa);
      myDescriptors.add(descriptor);
    }

    private ReferenceLink findReferenceLink(BugzillaAttribute attribute) {
      return Util.castNullable(ReferenceLink.class, ATTR_TO_LINK.get(attribute));
    }

    public BaseEnumConstraintDescriptor registerEnumWithResolver(BugzillaAttribute attribute, boolean multivalue,
      ResolvedFactory factory)
    {
      return registerEnum(attribute, getWorkspaceAttribute(attribute),
        BugzillaUtil.getDisplayableFieldName(attribute), EnumNarrower.DEFAULT, multivalue, factory);
    }

    private BaseEnumConstraintDescriptor registerEnum(BugzillaAttribute attribute, DBAttribute wsAttribute,
      String displayableName, EnumNarrower narrower, boolean isSet, ResolvedFactory factory)
    {
      final ReferenceLink link = findReferenceLink(attribute);
      final BaseEnumConstraintDescriptor descriptor = BaseEnumConstraintDescriptor.createStarted(
        wsAttribute, narrower, displayableName, factory, null, null,
        isSet ? EnumConstraintKind.INTERSECTION : EnumConstraintKind.INCLUSION, null, null, null, null, false,
        link.getReferentType(), myMd.getActor(NameResolver.ROLE).getCache());
      register(attribute, wsAttribute, descriptor);
      return descriptor;
    }

    public void register(BugzillaAttribute attribute, DBAttribute wsAttribute,
      BaseEnumConstraintDescriptor descriptor)
    {
      myDescriptors.add(descriptor);
      myEnumDescriptors.put(attribute, descriptor);
      myEnumDescriptorsByAttribute.put(wsAttribute, descriptor);
    }

    public void registerComments() {
      String displayName = Local.text("bz.field.Comments", "Comments");
      myDescriptors.add(new CommentsDescriptor(displayName, CommentsLink.attrMaster, CommentsLink.attrText));
    }
  }

  private static class BugResolver extends TextResolver {
    public boolean isSameArtifact(ItemKey key, String text) {
      return key.getId().equals(text);
    }

    public ItemKey getItemKey(@NotNull String text) {
      return new UnresolvedItem(this, text);
    }

    public long resolve(String text, UserChanges changes) throws BadItemKeyException {
      if(text == null || (text = text.trim()).isEmpty()) {
        return 0L;
      }

      final int bugId;
      try {
        bugId = Integer.parseInt(text);
      } catch(NumberFormatException e) {
        throw new BadItemKeyException(text);
      }

      final Engine engine = changes.getActor(Engine.ROLE);
      final BoolExpr<DP> filter = DPEquals.create(Bug.attrBugID, bugId);

      final Connection connection = engine.getConnectionManager().findByItem(changes.getConnectionItem());
      if(connection == null) {
        throw new BadItemKeyException(L.content("cannot process bug id " + bugId + ": no connection"));
      }

      final LongArray bugs = connection.getViews().getConnectionItems().filter(filter)
        .query(changes.getCreator().getReader()).copyItemsSorted();
      if (bugs.size() == 1) {
        return bugs.get(0);
      } else if (bugs.size() == 0) {
        String message =
          Local.parse("Cannot find " + Terms.ref_artifact + " with id " + bugId + " in the local database");
        throw new BadItemKeyException(message);
      } else {
        // bugs.size() > 1
        throw new BadItemKeyException(Local.parse(
          "There are " + bugs.size() + " " + Terms.ref_artifacts + " with id " + bugId + " in the local database"));
      }
    }
  }


  public static class LinksCollector {
    private final List<DataLink> links = Collections15.arrayList();
    private final Map<BugzillaAttribute, BugzillaAttributeLink> attrToLink = Collections15.hashMap();

    public <T> void linkScalar(DBAttribute<T> attribute, BugzillaAttribute bugzillaAttribute) {
      link(new ScalarLink<T>(attribute, bugzillaAttribute, false, true, false));
    }

    public void linkText(DBAttribute<String> attribute, BugzillaAttribute bzAttribute) {
      link(new ScalarLink.Text(attribute, bzAttribute, true, false));
    }

    public void link(DataLink link) {
      links.add(link);
      if(link instanceof BugzillaAttributeLink) {
        attrToLink.put(((BugzillaAttributeLink)link).getBugzillaAttribute(), (BugzillaAttributeLink) link);
      }
    }
  }
}