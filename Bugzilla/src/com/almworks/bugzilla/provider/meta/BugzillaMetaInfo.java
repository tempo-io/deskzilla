package com.almworks.bugzilla.provider.meta;

import com.almworks.api.actions.StdItemActions;
import com.almworks.api.application.*;
import com.almworks.api.application.field.CustomFieldsHelper;
import com.almworks.api.application.field.ItemField;
import com.almworks.api.application.util.*;
import com.almworks.api.application.viewer.textdecorator.TextDecoratorRegistry;
import com.almworks.api.edit.ItemCreator;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.rules.*;
import com.almworks.api.gui.MainMenu;
import com.almworks.api.search.TextSearch;
import com.almworks.bugzilla.gui.*;
import com.almworks.bugzilla.gui.attachments.*;
import com.almworks.bugzilla.gui.comments.*;
import com.almworks.bugzilla.gui.flags.edit.EditFlagsAction;
import com.almworks.bugzilla.gui.view2.BugzillaView2;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.attachments.AttachmentsModelKey;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.flags2.FlagsModelKey;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.engine.gui.ItemTableBuilder;
import com.almworks.engine.gui.LeftFieldsBuilder;
import com.almworks.integers.LongList;
import com.almworks.integers.util.LongListConcatenation;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.timetrack.gui.TimeTrackingActions;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.LongSet;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Function2;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.ToolbarBuilder;
import com.almworks.util.components.renderer.FontStyle;
import com.almworks.util.components.renderer.RendererContext;
import com.almworks.util.components.renderer.table.TextCell;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.files.ExternalBrowser;
import com.almworks.util.images.Icons;
import com.almworks.util.text.TextUtil;
import com.almworks.util.threads.ThreadAWT;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.almworks.bugzilla.integration.BugzillaAttribute.*;
import static com.almworks.bugzilla.provider.meta.BugzillaKeys.*;
import static org.almworks.util.Collections15.NNList;

public class BugzillaMetaInfo extends BaseMetaInfo {
  public static final TypedKey<AnAction> DELETE_BUG_ACTION =
    TypedKey.create("BugzillaMetaInfo.DeleteBugAction", AnAction.class);
  public static final TypedKey<AnAction> CHANGE_VOTE_ACTION =
    TypedKey.create("BugzillaMetaInfo.ChangeVoteAction", AnAction.class);
  public static final TypedKey<AnAction> TOGGLE_VOTE_ACTION =
    TypedKey.create("BugzillaMetaInfo.ToggleVoteAction", AnAction.class);
  public static final TypedKey<AnAction> EDIT_CC_LIST_ACTION =
    TypedKey.create("BugzillaMetaInfo.EditCCListAction", AnAction.class);


  private static final Function<ItemWrapper, BaseAutoAssignAction.Edit> GET_ASSIGN = new GetAssignEdit();
  public static final AnAction AUTO_ASSIGN_ACTION = new AutoAssignAction(StdItemActions.STD_AUTO_ASSIGN_PRESENTATION, PresentationKey.NAME, GET_ASSIGN);
  public static final AnAction EDITOR_AUTO_ASSIGN_ACTION = new EditorAutoAssignAction(StdItemActions.STD_AUTO_ASSIGN_PRESENTATION, PresentationKey.NAME, GET_ASSIGN);

  private final ElementViewer.CompositeFactory<ItemUiModel> myViewers;
  private final ElementViewer.CompositeFactory<ItemUiModel> myEditors;
  private final ElementViewer.CompositeFactory<ItemUiModel> myCreators;
  private final DummyBugKey myDummy;
  private final AListModel<ItemsTreeLayout> myTreeLayouts;
  private ToolbarBuilder mySingleToolbarBuilder;
  private ToolbarBuilder mySeveralToolbarBuilder;
  private final Map<TypedKey<AnAction>, AnAction> myCustomActions;
  private final Configuration myConfig;
  private final VerifierManager myVerifierManager;

  public BugzillaMetaInfo(List<AnAction> actions, AListModel<ItemsTreeLayout> treeLayouts, TextDecoratorRegistry decorators, Map<TypedKey<AnAction>, AnAction> customActions,
    Configuration config)
  {
    super(BugzillaKeys.getKeys(), getKeyMap(), actions);
    myDummy = dummy;
    myCustomActions = customActions;
    myConfig = config;
    myViewers = ElementViewer.CompositeFactory.create();
    myEditors = ElementViewer.CompositeFactory.create();
    myCreators = ElementViewer.CompositeFactory.create();
    BugzillaCreateView.addFactory(myCreators);
//    BugViewPanel.addFactory(myViewers, commentsKey, id);
    BugzillaView2.addFactory(myViewers, this, decorators);
    BugzillaEditView.addFactory(myEditors);
    myTreeLayouts = treeLayouts;
    myVerifierManager = new BugzillaVerifierManager();
  }

  public String getTypeId() {
    return "Bugzilla.Bug";
  }

  public ModelKey<Boolean> getEditBlockKey() {
    return myDummy;
  }

  public String getDisplayableType() {
    return "bug";
  }

  public ElementViewer<ItemUiModel> createViewer(Configuration config) {
    return new BugzillaBugViewer(myDummy, myViewers.createViewer(config));
  }

  public ItemCreator newCreator(Configuration configuration) {
    return new BugCreator(myCreators.createViewer(configuration));
  }

  public ElementViewer<ItemUiModel> createEditor(Configuration configuration) {
    Threads.assertAWTThread();
    return myEditors.createViewer(configuration);
  }

  @Override
  public LongList getSlavesToLockForEditor(ItemWrapper editedItem) {
    return new LongListConcatenation(
      LongSet.collect(UiItem.GET_ITEM, NNList(editedItem.getModelKeyValue(FlagsModelKey.MODEL_KEY))),
      LongSet.collect(UiItem.GET_ITEM, NNList(editedItem.getModelKeyValue(AttachmentsModelKey.INSTANCE)))
    );
  }

  @Override
  public LongList getSlavesToLockForEditor(ItemVersion itemVersion) {
    return new LongListConcatenation(
      itemVersion.getSlaves(Flags.AT_FLAG_MASTER),
      itemVersion.getSlaves(AttachmentsLink.attrMaster)
    );
  }

  @Nullable
  protected AnAction getStdAction(String id, ActionRegistry registry) throws CantPerformException {
    if (ADD_COMMENT.equals(id))
      return AddCommentAction.INSTANCE;
    if (EDIT_COMMENT.equals(id))
      return EditCommentAction.EDIT_COMMENT;
    else if (REPLY_TO_COMMENT.equals(id))
      return ReplyToCommentEditAction.INSTANCE;
    else if (ATTACH_FILE.equals(id))
      return BugzillaAttachFileAction.INSTANCE;

    else if (ATTACH_SCREENSHOT.equals(id))
      return BugzillaAttachScreenshotAction.INSTANCE;

    else if (AUTO_ASSIGN.equals(id))
      return AUTO_ASSIGN_ACTION;
    else if (REMOVE_COMMENT.equals(id))
      return DeleteCommentAction.INSTANCE;
    else {
      assert false : id;
      throw new CantPerformException(id);
    }
  }

  public AListModel<ItemsTreeLayout> getTreeLayouts() {
    return myTreeLayouts;
  }

  static String label(BugzillaAttribute attribute) {
    return BugzillaUtil.getDisplayableFieldName(attribute) + ":";
  }

  public String getPartialDownloadHtml() {
    return "This bug is not fully downloaded.<br>Detailed vote info, groups and other are not downloaded yet.";
  }

  public void setupEditToolbar(AToolbar toolbar) {
    final Map<String, PresentationMapping<?>> mapping = PresentationMapping.VISIBLE_NONAME;
    toolbar.addAction(BugzillaAttachmentsController.ATTACH_FILE).overridePresentation(mapping);
    toolbar.addAction(BugzillaAttachmentsController.ATTACH_SCREENSHOT).overridePresentation(mapping);
  }

  public void addLeftFields(final ItemTableBuilder builder) {
    builder.addString(label(ALIAS), alias, true);
    builder.addItem(label(PRODUCT), product);
    builder.addItem(label(VERSION), version);
    builder.addItem(label(COMPONENT), component);

    builder.addSeparator();

    builder.addDate(label(CREATION_TIMESTAMP), creationTime, false, true, false);
    builder.addDate(label(MODIFICATION_TIMESTAMP), modificationTime, false, true, false);

    builder.addSeparator();

    builder.addItem(label(STATUS), status);
    builder.addItem(label(RESOLUTION), resolution,
      new ItemKeyVisibility(resolution, NOT_AVAILABLE));
    builder.createLineBuilder(label(DUPLICATE_OF))
      .setStringValue(duplicateOf, true)
      .addAction("View bug", Icons.SEARCH_SMALL, new LeftFieldsBuilder.Action<String>(duplicateOf) {
        protected void act(ActionContext context, RendererContext rendererContext, @NotNull Connection connection,
          @NotNull String value) throws CantPerformException
        {
          context.getSourceObject(TextSearch.ROLE).search(value, connection, new SimpleTabKey());
        }
      })
      .addLine();
    builder.addItem(label(PRIORITY), priority);
    builder.addItem(label(SEVERITY), severity);

    builder.addItem(label(TARGET_MILESTONE), milestone,
      new ItemKeyVisibility(milestone, NO_MILESTONE));

    builder.addSeparator();

    builder.addItem(label(REPORTER), reporter);
    builder.addItem(label(ASSIGNED_TO), assignedTo);
    builder.addItem(label(QA_CONTACT), qaContact, new Function<ItemKey, Boolean>() {
      public Boolean invoke(ItemKey value) {
        return value != null && value.getDisplayName().length() > 0;
      }
    });
    // todo shorten list
    builder.addItemList(label(CC), cc, true, ItemKey.DISPLAY_NAME_ORDER);

    builder.addSeparator();

    builder.addItem(label(PLATFORM), platform);
    builder.addItem(label(OPERATING_SYSTEM), os);

    builder.createLineBuilder(label(BUG_URL))
      .setStringValue(url, false)
      .setVisibility(new Function<RendererContext, Boolean>() {
        public Boolean invoke(RendererContext context) {
          String url = Util.NN(LeftFieldsBuilder.getModelValueFromContext(context, BugzillaKeys.url));
          return url.length() > 0 && !url.equals("http://");
        }
      })
      .addAction("Open in browser", Icons.GLOBE_SMALL, new LeftFieldsBuilder.Action<String>(url) {
        protected void act(ActionContext context, RendererContext rendererContext, @NotNull Connection connection,
          @NotNull String value) throws CantPerformException
        {
          String url = Util.NN(value);
          if (url.indexOf("://") >= 0) {
            ExternalBrowser.openURL(url, true);
          }
        }
      })
      .addLine();

    SeeAlsoSupport.addLeftField(builder);

    builder.addString(label(STATUS_WHITEBOARD), statusWhiteboard, true);
    builder.addLine(label(KEYWORDS), new Function<ModelMap, String>() {
      public String invoke(ModelMap argument) {
        Collection<ItemKey> value = keywords.getValue(argument);
        if (value == null || value.isEmpty()) {
          return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ItemKey artifactKey : value) {
          sb.append(artifactKey.getDisplayName()).append(' ');
        }
        return sb.toString();
      }
    }, new Function<ModelMap, Boolean>() {
      public Boolean invoke(ModelMap argument) {
        Collection<ItemKey> value = keywords.getValue(argument);
        return value != null && !value.isEmpty();
      }
    });
    builder.addDecimal(label(ESTIMATED_TIME), estimatedTime, true);
//    builder.addDecimal(label(BugzillaAttribute.ACTUAL_TIME), BugzillaKeys.workedTime, true); // debug only
    builder.addDecimal(label(ACTUAL_TIME), totalWorkTime, true);
    builder.addDecimal(label(REMAINING_TIME), remainingTime, true);
    builder.addDate(label(DEADLINE), deadline, true, false, false);

    addLinkList(builder, BLOCKED_BY, depends, "View blocking bugs");
    addLinkList(builder, BLOCKS, blocks, "View depending bugs");

    addGroups(builder);

    builder.addSeparator();

    // todo B-1-7: verify on screen
    builder.addLine(label(TOTAL_VOTES), new Function<ModelMap, String>() {
      public String invoke(ModelMap map) {
        StringBuilder sb = new StringBuilder();
        Integer total = voteKeys.votes.getValue(map);
        if (total != null && total > 0) {
          sb.append(total);
        }
        Integer our = voteKeys.votesMy.getValue(map);
        if (our != null && our > 0) {
          if (sb.length() > 0) {
            sb.append(" ");
          }
          sb.append("my: ").append(our);
        }
        return sb.toString();
      }
    }, new Function<ModelMap, Boolean>() {
      public Boolean invoke(ModelMap argument) {
        Integer value = voteKeys.votes.getValue(argument);
        Integer our = voteKeys.votesMy.getValue(argument);
        return (value != null && value > 0) || (our != null && our > 0);
      }
    });

    builder.addItemLines(customFields, new Function2<ModelKey, ModelMap, ItemField>() {
      public ItemField invoke(ModelKey modelKey, ModelMap modelMap) {
        return CustomFieldsHelper.getField((ModelKey<?>) modelKey, modelMap);
      }
    });
  }

  @ThreadAWT
  @NotNull
  @Override
  public VerifierManager getVerifierManager() {
    return myVerifierManager;
  }

  private static void addGroups(ItemTableBuilder fields) {
    Function<ModelMap, String> getter = new Function<ModelMap, String>() {
      public String invoke(ModelMap context) {
        BugGroupInfo info = groups.getValue(context);
        return info == null ? "" : info.toDisplayString();
      }
    };
    Function<ModelMap, Boolean> contextBooleanFunction = new Function<ModelMap, Boolean>() {
      public Boolean invoke(ModelMap context) {
        BugGroupInfo info = groups.getValue(context);
        return info != null && info.toDisplayString().length() > 0;
      }
    };
    fields.addLine("Groups:", getter, contextBooleanFunction);
  }

  private static void addLinkList(ItemTableBuilder fields, BugzillaAttribute attribute,
    final ModelKey<Collection<ItemKey>> key, String actionTooltip)
  {
    fields.createLineBuilder(label(attribute))
      .setValueCell(new TextCell(FontStyle.BOLD, new Function<RendererContext, String>() {
        public String invoke(RendererContext context) {
          return getBugIdList(context, key);
        }
      }))
      .setVisibility(new LeftFieldsBuilder.NotEmptyCollection(key))
      .addAction(actionTooltip, Icons.SEARCH_SMALL, new LeftFieldsBuilder.Action<Collection<ItemKey>>(key) {
        protected void act(ActionContext context, RendererContext rendererContext, @NotNull Connection connection,
          @NotNull Collection<ItemKey> value) throws CantPerformException
        {
          String list = getBugIdList(rendererContext, key);
          if (list.length() > 0) {
            context.getSourceObject(TextSearch.ROLE).search(list, connection, new SimpleTabKey());
          }
        }
      })
      .addLine();
  }

  private static String getBugIdList(RendererContext context, ModelKey<Collection<ItemKey>> modelKey) {
    Collection<ItemKey> value = LeftFieldsBuilder.getModelValueFromContext(context, modelKey);
    if (value == null || value.isEmpty())
      return "";
    else
      return TextUtil.separate(value, ", ", ItemKey.DISPLAY_NAME);
  }

  private static class ItemKeyVisibility implements Function<ItemKey, Boolean> {
    private final String myHideValue;

    public ModelKey<ItemKey> getKey() {
      return myKey;
    }

    private final ModelKey<ItemKey> myKey;

    public ItemKeyVisibility(ModelKey<ItemKey> key, String hideValue) {
      myKey = key;
      myHideValue = hideValue;
    }


    public Boolean invoke(ItemKey value) {
      return value != null && !myHideValue.equals(value.getDisplayName());
    }
  }

  public ToolbarBuilder getToolbarBuilder(boolean singleItem) {
    if (singleItem) {
      if (mySingleToolbarBuilder == null) mySingleToolbarBuilder = createToolbar(true);
      return mySingleToolbarBuilder;
    } else {
      if (mySeveralToolbarBuilder == null) mySeveralToolbarBuilder = createToolbar(false);
      return mySeveralToolbarBuilder;
    }
  }

  private ToolbarBuilder createToolbar(boolean singleArtifact) {
    final boolean hideDisabled = true;
    final ToolbarBuilder builder =
      hideDisabled ? ToolbarBuilder.smallEnabledButtons() : ToolbarBuilder.smallVisibleButtons();

    builder.addAction(MainMenu.Edit.EDIT_ITEM);
    builder.addAction(getCustomAction(DELETE_BUG_ACTION));
    builder.addAction(StdItemActions.ADD_COMMENT);
    builder.addAction(StdItemActions.REPLY_TO_COMMENT);
    builder.addAction(StdItemActions.EDIT_COMMENT);
    builder.addAction(StdItemActions.REMOVE_COMMENT);
    builder.addAction(StdItemActions.ATTACH_FILE);
    builder.addAction(StdItemActions.ATTACH_SCREENSHOT);

    final WorkflowComponent2 wfc2 = Context.get(WorkflowComponent2.class);
    if (wfc2 != null) builder.add(new WorkflowToolbarEntry(myConfig.getOrCreateSubset("workflow"), wfc2));

    builder.addAction(MainMenu.Edit.DOWNLOAD);
    if (!singleArtifact) {
      builder.addAction(MainMenu.Edit.UPLOAD);
      builder.addAction(MainMenu.Edit.DISCARD);
    }

    builder.addAction(MainMenu.Edit.TAG);
    builder.addAction(getCustomAction(TOGGLE_VOTE_ACTION));
    builder.addAction(getCustomAction(CHANGE_VOTE_ACTION));
    builder.addAction(getCustomAction(EDIT_CC_LIST_ACTION));
    builder.addAction(EditFlagsAction.INSTANCE);

    builder.addAction(TimeTrackingActions.START_WORK);
    builder.addAction(TimeTrackingActions.STOP_WORK);

    return builder;
  }

  public AnAction getCustomAction(TypedKey<AnAction> key) {
    return key.getFrom(myCustomActions);
  }

  private static class GetAssignEdit implements Function<ItemWrapper, BaseAutoAssignAction.Edit> {
    @Override
    public BaseAutoAssignAction.Edit invoke(ItemWrapper wrapper) {
      BugzillaContext context = BugzillaUtil.getContext(wrapper);
      if (context == null) {
        Log.warn("Auto-assign cannot be applied to items not belonging to a Bugzilla connection");
        return null;
      }
      final PrivateMetadata pm = context.getPrivateMetadata();
      return new BaseAutoAssignAction.Edit() {
        @Override
        public void editOnForm(ItemUiModel formModel, String user) {
          ModelKeyUtils.setModelValue(assignedTo, assignedTo.getResolver().convert(user), formModel.getModelMap());
        }

        @Override
        public void editDb(ItemVersionCreator item, String userId) {
          long user = User.getOrCreateFromUserInput(item, userId, pm);
          item.setValue(Bug.attrAssignedTo, user);
        }
      };
    }
  }
}
