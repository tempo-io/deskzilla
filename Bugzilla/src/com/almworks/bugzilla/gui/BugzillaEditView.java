package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.util.ElementViewerImpl;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.api.install.TrackerProperties;
import com.almworks.bugzilla.gui.attachments.BugzillaAttachmentsController;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.attachments.AttachmentInfo;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.bugzilla.provider.meta.BugzillaMetaInfo;
import com.almworks.engine.gui.ItemMessages;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.Env;
import com.almworks.util.L;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.*;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.i18n.ComponentLocalizerVisitor;
import com.almworks.util.i18n.NameBasedComponentLocalizer;
import com.almworks.util.model.ValueModel;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.PresentationMapping;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.math.BigDecimal;

/**
 * @author : Dyoma
 */
public class BugzillaEditView implements UIComponentWrapper2 {
  private final JTextPane myNewComment = new JTextPane();
  private final JCheckBox myNewCommentPrivate = new JCheckBox("Private comment");
  private final AScrollPane myNewCommentScrollPane = new AScrollPane(myNewComment);
  private final JPanel myNewCommentPanel = new JPanel(new BorderLayout());

  public JPanel myWholePanel;
  private JPanel myHeader;
  private CompletingComboBox<ItemKey> myAssignTo;
  private CompletingComboBox<ItemKey> myQAContact;
  private JPanel myDuplicateOfPanel;
  private AComboBox<?> myResolution;
  private AComboBox<?> myStatus;
  private AComboBox<?> myProduct;
  private AComboBox<?> myVersion;
  private AComboBox<?> myComponent;
  private AComboBox<?> myMilestone;
  private JLabel myProductLabel;
  private JLabel myHardwareLabel;
  private JLabel myOSLabel;
  private JLabel myVersionLabel;
  private JLabel myPriorityLabel;
  private JLabel mySeverityLabel;
  private JLabel myComponentLabel;
  private JLabel mySummaryLabel;
  private JLabel myStatusLabel;
  private JLabel myResolutionLabel;
  private JLabel myAssignedToLabel;
  private Link myURLLink;
  private JTextField myURLField;
  private JLabel myKeywordsLabel;
  private JLabel myDependsOnLabel;
  private JLabel myBlocksLabel;
  private JLabel myAdditionalCommentLabel;
  private JTextField myLastModified;
  private JTextField myDepends;
  private JTextField myBlocks;
  private PlaceHolder myCCPlace;
  private JLabel myCCLabel;
  private PlaceHolder myAdditionalCommentPlace;

  private final DetachComposite myUIDetach = new DetachComposite();
  private final JScrollPane myScrollpane;
  private final JPanel myFormAndMessages = new JPanel(new BorderLayout());
  private static final int COMMENT_ROWS = 10;
  private static final int COMMENT_PREFERRED_COLUMNS = 80;
  private JTextField mySummary;
  private JTextField myStatusWhiteboard;
  private FieldWithMoreButton myKeywords;
  private AComboBox myPlatform;
  private AComboBox myOS;
  private AComboBox myPriority;
  private AComboBox mySeverity;
  private AToolbarButton myAutoAssignButton;
  private JPanel myGroupsPanel;
  private JPanel myGroupsPanelHolder;
  private JPanel myFieldsPanel;
  private JTextField myAlias;
  private JLabel myAliasLabel;
  private JTextField mySeeAlso;
  private ASortedTable<AttachmentInfo> myAttachmentsTable;
  private JPanel myAttachmentsPanel;
  private JLabel myAttachmentsLabel;
  private final ValueModel<BigDecimal> myAddedHoursModel = ValueModel.create();

  private final OptionalFieldsController myOptionalFields = OptionalFieldsController.editBug(myFieldsPanel);

  public BugzillaEditView() {
    myNewCommentScrollPane.setAdaptiveVerticalScroll(true);
    UIUtil.configureBasicScrollPane(myNewCommentScrollPane, 30, 5);

    myNewCommentPanel.add(myNewCommentScrollPane, BorderLayout.CENTER);
    myNewCommentPanel.add(myNewCommentPrivate, BorderLayout.SOUTH);
    myNewCommentPrivate.setVisible(false);
    myAdditionalCommentPlace.show(myNewCommentPanel);

    NameBasedComponentLocalizer localizer = new NameBasedComponentLocalizer("bz.form.label.", JLabel.class);
    setupLocal(localizer);
    initOptionalFields();
    myCCPlace.show(CCListController2.createEditor(myCCLabel));
    BugzillaFormUtils.setupReadOnlyFields(myWholePanel, true);
    setupResolution();
    setupControllers();
    setupVisual();
    setupURLField(localizer);
    BugzillaFormUtils.setupKeywords(BugzillaKeys.keywords, myKeywords);
    BugzillaFormUtils.setupSeeAlso(BugzillaKeys.seeAlso, mySeeAlso);

    BugzillaFormUtils.setupGroupsPanel(myGroupsPanel, BugzillaKeys.groups);
    myGroupsPanelHolder.setPreferredSize(UIUtil.getRelativeDimension(myGroupsPanelHolder, 0, 8));

    myScrollpane = new JScrollPane(new ScrollablePanel(myWholePanel));
    myScrollpane.getViewport().setBackground(myWholePanel.getBackground());
    myFormAndMessages.add(myScrollpane, BorderLayout.CENTER);
    myFormAndMessages.add(ItemMessages.createMessagesForEditor(), BorderLayout.SOUTH);

    setupLabelMnemonics();
    myAutoAssignButton.overridePresentation(PresentationMapping.NONAME);
    myAutoAssignButton.setAnAction(BugzillaMetaInfo.EDITOR_AUTO_ASSIGN_ACTION);

    FieldsPanelController.install(myFieldsPanel,
      new TimeFieldsController.EditBug(myAddedHoursModel),
      new CustomFieldsController(myFieldsPanel, BugzillaKeys.customFields, false),
      myOptionalFields, new FlagsController());

    Aqua.cleanScrollPaneBorder(myScrollpane);
    Aero.cleanScrollPaneBorder(myScrollpane);
    Aqua.cleanScrollPaneResizeCorner(myScrollpane);
    UIUtil.setDefaultLabelAlignment(myWholePanel);
    Aqua.disableMnemonics(myWholePanel);
    BugzillaFormUtils.addRecentSupport(myPriority, mySeverity, myPlatform, myOS, myStatus);
  }

  private void setupLocal(NameBasedComponentLocalizer localizer) {
    ComponentLocalizerVisitor localizerVisitor =
      new ComponentLocalizerVisitor(localizer);
    UIUtil.updateComponents(myWholePanel, localizerVisitor);
    myOptionalFields.localize(localizerVisitor);
  }

  private void initOptionalFields() {
    myQAContact = myOptionalFields.getQaContact();
    myMilestone = myOptionalFields.getMilestone();
    mySeeAlso = myOptionalFields.getSeeAlso();
    myStatusWhiteboard = myOptionalFields.getStatusWhiteboard();
  }

  private void setupURLField(NameBasedComponentLocalizer localizer) {
    myURLLink.setName("url");
    localizer.localizeLink(myURLLink);
    myUIDetach.add(UIUtil.listenURLLinkSource(myURLLink, myURLField));
  }

  private void setupVisual() {
    myWholePanel.setBorder(UIUtil.EDITOR_PANEL_BORDER);
    new DocumentFormAugmentor().augmentForm(myUIDetach, myWholePanel, true);

    myResolution.setMaximumRowCount(BugResolution.count());
    myStatus.setMaximumRowCount(BugStatus.count());

    int count = Env.getInteger(TrackerProperties.AUTO_DETAIL_DELAY, 1, 99, 8);
    myProduct.setMaximumRowCount(count);
    myComponent.setMaximumRowCount(count);
    myAssignTo.setMaximumRowCount(count);
    myQAContact.setMaximumRowCount(count);
    myPlatform.setMaximumRowCount(count);
    myOS.setMaximumRowCount(count);
    myVersion.setMaximumRowCount(count);
    myPriority.setMaximumRowCount(count);
    mySeverity.setMaximumRowCount(count);
    myMilestone.setMaximumRowCount(count);

    myHeader.setBorder(new CompoundBorder(UIUtil.createSouthBevel(null), new EmptyBorder(0, 0, 4, 0)));
    myNewCommentScrollPane.setPreferredSize(
      UIUtil.getRelativeDimension(myNewCommentScrollPane, COMMENT_PREFERRED_COLUMNS, COMMENT_ROWS));

    myAttachmentsLabel.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
  }

  private void setupControllers() {
    BugzillaProvider provider = Context.require(BugzillaProvider.class);
    CommonMetadata md = provider.getCommonMetadata();
    BugzillaFormUtils.setupUserField(myAssignTo, BugzillaKeys.assignedTo, md.getEnumDescriptor(BugzillaAttribute.ASSIGNED_TO), UIControllerUtil.DEFAULT_CUBE_CONVERTOR, "<unassigned>");
    BugzillaFormUtils.setupUserField(myQAContact, BugzillaKeys.qaContact, md.getEnumDescriptor(BugzillaAttribute.QA_CONTACT), UIControllerUtil.DEFAULT_CUBE_CONVERTOR, "");

    ProductAndDependantsController.install(myProduct, myComponent, myVersion, myMilestone, false);
    setupNewComment();
    UIUtil.copyFocusTraversalKeys(mySummary, myNewComment);
    SpellCheckManager.attach(myUIDetach,mySummary);
    UIController.CONTROLLER.putClientValue(myLastModified, new LastModifiedLabelController());
    UIController.CONTROLLER.putClientValue(myAlias, new AliasController(BugzillaKeys.alias));
    BugListController.install(myDepends, BugzillaKeys.depends);
    BugListController.install(myBlocks, BugzillaKeys.blocks);

    BugzillaAttachmentsController.install(myAttachmentsTable, md.getEditorAttachmentsConfig(), false, BugzillaKeys.attachments, myAttachmentsPanel);
  }


  private void setupNewComment() {
    SpellCheckManager.attach(myUIDetach, myNewComment);
    UIController.CONTROLLER.putClientValue(myNewComment,
      new NewCommentController(myNewCommentPrivate, myAddedHoursModel));
  }

  private void setupResolution() {
    UIController.CONTROLLER.putClientValue(myResolution, new ResolutionController(myDuplicateOfPanel, myResolutionLabel));
  }

  private void setupLabelMnemonics() {
    myHardwareLabel.setLabelFor(myPlatform);
    myProductLabel.setLabelFor(myProduct);
    myOSLabel.setLabelFor(myOS);
    myComponentLabel.setLabelFor(myComponent);
    myVersionLabel.setLabelFor(myVersion);
    myStatusLabel.setLabelFor(myStatus);
    myPriorityLabel.setLabelFor(myPriority);
    myResolutionLabel.setLabelFor(myResolution);
    mySeverityLabel.setLabelFor(mySeverity);
    myAssignedToLabel.setLabelFor(myAssignTo);
    mySummaryLabel.setLabelFor(mySummary);
    myDependsOnLabel.setLabelFor(myDepends);
    myBlocksLabel.setLabelFor(myBlocks);
    myAdditionalCommentLabel.setLabelFor(myNewComment);
    myKeywordsLabel.setLabelFor(myKeywords);
    myAliasLabel.setLabelFor(myAlias);
  }

  public JComponent getComponent() {
    return myFormAndMessages;
  }

  public void dispose() {
    myUIDetach.detach();
  }

  public Detach getDetach() {
    return myUIDetach;
  }

  public static void addFactory(ElementViewer.CompositeFactory<ItemUiModel> builder) {
    builder.addViewer(L.listItem("Edit bug form"), new Convertor<Configuration, ElementViewer<ItemUiModel>>() {
      public ElementViewer<ItemUiModel> convert(Configuration config) {
        return new ElementViewerImpl(new BugzillaEditView(), null);
      }
    });
  }

  private class AliasController implements UIController<JTextField> {
    private final ModelKey<String> myKey;

    public AliasController(ModelKey<String> key) {
      myKey = key;
    }

    public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull final JTextField component) {
      BugzillaContext context = BugzillaUtil.getContext(LoadedItemServices.VALUE_KEY.getValue(model));
      OptionalFieldsTracker tracker = context != null ? context.getOptionalFieldsTracker() : null;
      if (tracker != null && tracker.isUnused(BugzillaAttribute.ALIAS)) {
        myAlias.setEnabled(false);
        myAliasLabel.setEnabled(false);
      }
      Document swingModel = myKey.getModel(lifespan, model, Document.class);
      component.setDocument(swingModel);
      lifespan.add(new Detach() {
        protected void doDetach() {
          component.setDocument(new PlainDocument());
        }
      });
    }
  }

}