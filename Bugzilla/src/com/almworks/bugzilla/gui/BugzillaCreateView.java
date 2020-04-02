package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.util.ElementViewerImpl;
import com.almworks.api.explorer.util.UIControllerUtil;
import com.almworks.api.install.TrackerProperties;
import com.almworks.bugzilla.gui.attachments.BugzillaAttachmentsController;
import com.almworks.bugzilla.integration.BugStatus;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.attachments.AttachmentInfo;
import com.almworks.bugzilla.provider.comments.CommentListModelKey;
import com.almworks.bugzilla.provider.comments.DescriptionModelKey;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.Env;
import com.almworks.util.L;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.ElementVisitor;
import com.almworks.util.commons.Factory;
import com.almworks.util.components.*;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.exec.Context;
import com.almworks.util.i18n.ComponentLocalizerVisitor;
import com.almworks.util.i18n.NameBasedComponentLocalizer;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;

public class BugzillaCreateView implements UIComponentWrapper2 {
  private final DetachComposite myUIDetach = new DetachComposite();
  private final JScrollPane myScrollPane;

  private JPanel myWholePanel;
  private CompletingComboBox<ItemKey> myAssignTo;
  private final CompletingComboBox<ItemKey> myQAContact;
  private JTextPane myDescription;
  private JLabel mySummaryLabel;
  private JLabel myDescriptionLabel;
  private AComboBox<?> myProduct;
  private AComboBox<?> myVersion;
  private AComboBox<?> myComponent;
  private final AComboBox<?> myMilestone;
  private JLabel myProductLabel;
  private JLabel myVersionLabel;
  private JLabel myComponentLabel;
  private JLabel myPlatformLabel;
  private JLabel myOSLabel;
  private JLabel myPriorityLabel;
  private JLabel mySeverityLabel;
  private JLabel myAssignedToLabel;
  private JLabel myKeywordsLabel;
  private Link myURLLink;
  private JTextField myURLField;
  private PlaceHolder myCCPlace;
  private JLabel myCCLabel;
  private AComboBox myPriority;
  private AComboBox mySeverity;
  private AComboBox myPlatform;
  private AComboBox myOS;
  private FieldWithMoreButton myKeywords;
  private JTextField mySummary;
  private JTextField myDepends;
  private JLabel myBlocksLabel;
  private JTextField myBlocks;
  private JLabel myDependsOnLabel;
  private JPanel myGroupsPanel;
  private JCheckBox myDescriptionPrivate;

  private JPanel myFieldsPanel;
  private JScrollPane myDescriptionScrollPane;
  private AComboBox myStatus;
  private ASortedTable<AttachmentInfo> myAttachmentsTable;
  private JLabel myAttachmentsLabel;
  private JPanel myHeader;
  private JPanel myFooter;

  private final OptionalFieldsController myOptionalFields;

  public BugzillaCreateView() {
    NameBasedComponentLocalizer localizer = new NameBasedComponentLocalizer("bz.form.label.", JLabel.class);
    ComponentLocalizerVisitor localizeVisitor = new ComponentLocalizerVisitor(localizer);
    UIUtil.updateComponents(myWholePanel, localizeVisitor);
    myOptionalFields = OptionalFieldsController.newBug(myFieldsPanel);
    myOptionalFields.localize(localizeVisitor);
    myQAContact = myOptionalFields.getQaContact();
    myMilestone = myOptionalFields.getMilestone();
    setupControllers();
    myCCPlace.show(CCListController2.createEditor(myCCLabel));
    setupVisual();
    setupURLField(localizer);
    BugzillaFormUtils.setupKeywords(BugzillaKeys.keywords, myKeywords);
    BugzillaFormUtils.setupGroupsPanel(myGroupsPanel, BugzillaKeys.groups);
    
    myScrollPane = new JScrollPane(new ScrollablePanel(myWholePanel)) {
      public void addNotify() {
        super.addNotify();
        JTextField field = mySummary;
        if (field != null) {
          field.requestFocusInWindow();
        }
      }
    };
    setupLabelMnemonics();

    Aqua.cleanScrollPaneBorder(myScrollPane);
    Aero.cleanScrollPaneBorder(myScrollPane);
    Aqua.cleanScrollPaneResizeCorner(myScrollPane);
    UIUtil.setDefaultLabelAlignment(myWholePanel);
    Aqua.disableMnemonics(myWholePanel);
    BugzillaFormUtils.addRecentSupport(myPriority, mySeverity, myPlatform, myOS, myStatus);
  }

  private void setupURLField(NameBasedComponentLocalizer localizer) {
    myURLLink.setName("url");
    localizer.localizeLink(myURLLink);
    myUIDetach.add(UIUtil.listenURLLinkSource(myURLLink, myURLField));
  }

  private void setupVisual() {
    myWholePanel.setBorder(UIUtil.EDITOR_PANEL_BORDER);
    new DocumentFormAugmentor().augmentForm(myUIDetach, myWholePanel, true);
//    UIUtil.adjustFont(mySummaryLabel, -1, Font.BOLD, false);
    UIUtil.adjustFont(myDescriptionLabel, -1, Font.BOLD, false);
    BugzillaFormUtils.setupReadOnlyFields(myWholePanel, false);
    final int line = UIUtil.getLineHeight(mySummary);
    UIUtil.addOuterBorder(myHeader, new EmptyBorder(0, 0, line / 2, 0));
    UIUtil.addOuterBorder(myHeader, UIUtil.createSouthBevel(null));
    UIUtil.addOuterBorder(myHeader, new EmptyBorder(0, 0, line / 2, 0));
    UIUtil.addOuterBorder(myFooter, new EmptyBorder(line, 0, 0, 0));

    int top = myDescriptionScrollPane.getBorder().getBorderInsets(myDescriptionScrollPane).top;
    top += myDescription.getBorder().getBorderInsets(myDescription).top;
    UIUtil.addOuterBorder(myDescriptionLabel, new EmptyBorder(top, 0, 0, 0));

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
    myStatus.setMaximumRowCount(BugStatus.count());

    myAttachmentsLabel.putClientProperty(UIUtil.SET_DEFAULT_LABEL_ALIGNMENT, false);
  }

  private int getBaseline(JComponent comp) {
    return comp.getBaseline(comp.getWidth(), comp.getHeight());
  }

  private void setupControllers() {
    final BugzillaProvider provider = Context.require(BugzillaProvider.class);
    final CommonMetadata md = provider.getCommonMetadata();

    BugzillaFormUtils.setupUserField(myAssignTo, BugzillaKeys.assignedTo, md.getEnumDescriptor(BugzillaAttribute.ASSIGNED_TO), UIControllerUtil.DEFAULT_CUBE_CONVERTOR, "<unassigned>");
    BugzillaFormUtils.setupUserField(myQAContact, BugzillaKeys.qaContact, md.getEnumDescriptor(BugzillaAttribute.QA_CONTACT), UIControllerUtil.DEFAULT_CUBE_CONVERTOR, "");

    setupDescription();

    UIUtil.copyFocusTraversalKeys(mySummary, myDescription);
    ProductAndDependantsController.install(myProduct, myComponent, myVersion, myMilestone, true);

    BugListController.install(myDepends, BugzillaKeys.depends);
    BugListController.install(myBlocks, BugzillaKeys.blocks);

    FieldsPanelController.install(myFieldsPanel,
      new TimeFieldsController.NewBug(),
      new CustomFieldsController(myFieldsPanel, BugzillaKeys.customFields, true),
      myOptionalFields, new FlagsController());

    UIController.CONTROLLER.putClientValue(myWholePanel, new UserModificationController());

    BugzillaAttachmentsController.install(myAttachmentsTable, md.getEditorAttachmentsConfig(), true, BugzillaKeys.attachments, myAttachmentsLabel);
  }

  private void setupDescription() {
    UIController.CONTROLLER.putClientValue(myDescription, new UIController<JTextPane>() {
      public void connectUI(Lifespan lifespan, final ModelMap model, final JTextPane component) {
        CommentListModelKey modelKey = BugzillaKeys.comments;
        final DescriptionModelKey key = modelKey.getDescriptionKey();
        StyledDocument document = key.getModel(lifespan, model, StyledDocument.class);

        UIUtil.setStyledDocument(lifespan, component, document);
        component.setDocument(document);

        BugzillaContext context = BugzillaUtil.getContext(model);

        SpellCheckManager.attach(lifespan, component);
        
        boolean visible = context != null && context.isCommentPrivacyAccessible();
        myDescriptionPrivate.setVisible(visible);
        if (visible) {
          myDescriptionPrivate.setSelected(false);
          ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              key.setDescriptionPrivate(model, myDescriptionPrivate.isSelected());
            }
          };
          UIUtil.addActionListener(lifespan, myDescriptionPrivate, listener);
          listener.actionPerformed(null);
        }

        lifespan.add(new Detach() {
          protected void doDetach() {
            component.setDocument(new DefaultStyledDocument());
          }
        });
      }
    });
  }

  private void setupLabelMnemonics() {
    myPlatformLabel.setLabelFor(myPlatform);
    myProductLabel.setLabelFor(myProduct);
    myOSLabel.setLabelFor(myOS);
    myComponentLabel.setLabelFor(myComponent);
    myVersionLabel.setLabelFor(myVersion);
    myPriorityLabel.setLabelFor(myPriority);
    mySeverityLabel.setLabelFor(mySeverity);
    myAssignedToLabel.setLabelFor(myAssignTo);
//    myURLLabel.setLabelFor(findComponent("url"));
    mySummaryLabel.setLabelFor(mySummary);
    myDescriptionLabel.setLabelFor(myDescription);
    myDependsOnLabel.setLabelFor(myDepends);
    myBlocksLabel.setLabelFor(myBlocks);
    myKeywordsLabel.setLabelFor(myKeywords);
  }

  public JComponent getComponent() {
    return myScrollPane;
  }

  public void dispose() {
    myUIDetach.detach();
  }

  public Detach getDetach() {
    return myUIDetach;
  }

  public static void addFactory(ElementViewer.CompositeFactory<ItemUiModel> builder) {
    builder.addViewer(L.listItem("New bug form"), new Factory<ElementViewer<ItemUiModel>>() {
      public ElementViewer<ItemUiModel> create() {
        return new ElementViewerImpl(new BugzillaCreateView(), null);
      }
    });
  }

  private void createUIComponents() {
    AScrollPane scrollPane = new AScrollPane(myDescription);
    scrollPane.setAdaptiveVerticalScroll(true);
    myDescriptionScrollPane = scrollPane;
    UIUtil.configureScrollpaneVerticalOnly(myDescriptionScrollPane);
  }

  /**
   * The controller responsible for tracking user modifications of
   * bug fields. Used to prevent overriding user-modifed values
   * with defaults upon switching Product or Component.
   * The idea is to attach specialized listeners to certain components
   * that set the modified-by-user flag for corresponding model keys.
   * Those keys need to properly implement the {@code UserModifiable}
   * interface (subclasses of {@code AttributeModelKey} do).
   * The {@code BugCreator} class respects this flag when setting
   * default values.
   * All listener and auxiliary interfaces are implemented by the
   * {@code UserModificationListener} class.
   * @see com.almworks.api.application.UserModifiable 
   * @see com.almworks.bugzilla.provider.meta.BugCreator
   * @see com.almworks.bugzilla.gui.BugzillaCreateView.UserModificationListener
   */
  private class UserModificationController implements UIController<JComponent> {
    public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull JComponent component) {
      final MetaInfo metaInfo = model.getMetaInfo();

      // A simple AnActionListener is used for AComboBoxes.
      connectAComboBox(lifespan, myStatus, model, metaInfo);
      connectAComboBox(lifespan, myPriority, model, metaInfo);
      connectAComboBox(lifespan, mySeverity, model, metaInfo);
      connectAComboBox(lifespan, myPlatform, model, metaInfo);
      connectAComboBox(lifespan, myOS, model, metaInfo);

      // For CompletingComboBoxes we use a rather hackish ItemListener.
      connectCComboBox(lifespan, myAssignTo, model, metaInfo);
      connectCComboBox(lifespan, myQAContact, model, metaInfo);

      // For the CC list we crawl the Swing tree to find a control
      // with a CCListController attached, and then install a
      // ChangeListener on that controller.
      connectCCList(lifespan, myCCPlace, model, metaInfo);

      DefaultUIController.connectChildren(component, lifespan, model);
    }

    private void connectAComboBox(Lifespan life, AComboBox<?> combo, ModelMap model, MetaInfo metaInfo) {
      final ModelKey<?> key = metaInfo.findKey(combo.getName());
      if(key instanceof UserModifiable) {
        life.add(combo.addActionListener(new UserModificationListener(key, model)));
      }
    }

    private void connectCComboBox(Lifespan life, CompletingComboBox<?> combo, ModelMap model, MetaInfo metaInfo) {
      final ModelKey<?> key = metaInfo.findKey(combo.getName());
      if(key instanceof UserModifiable) {
        life.add(UIUtil.addItemListener(combo, new UserModificationListener(key, model)));
      }
    }

    private void connectCCList(Lifespan life, PlaceHolder place, ModelMap model, MetaInfo metaInfo) {
      final ModelKey<?> key = BugzillaKeys.cc;
      if(key instanceof UserModifiable) {
        UIUtil.visitComponents(place, JComponent.class, new UserModificationListener(key, model, life));
      }
    }
  }

  /**
   * Utility listener used by the {@link UserModificationController}.
   * Implements a lot of interfaces, but only one or two of those are
   * relevant for any particular usage.
   * @see com.almworks.bugzilla.gui.BugzillaCreateView.UserModificationController
   */
  private static class UserModificationListener
    implements AnActionListener, ItemListener, ChangeListener, ElementVisitor<JComponent>
  {
    private final UserModifiable myKey;
    private final ModelMap myModel;
    private final Lifespan myLife;

    UserModificationListener(ModelKey<?> key, ModelMap model) {
      this(key, model, null);
    }

    UserModificationListener(ModelKey<?> key, ModelMap model, Lifespan life) {
      myKey = (UserModifiable)key;
      myModel = model;
      myLife = life;
    }

    private void setModified() {
      myKey.setUserModified(myModel, true);
    }

    public void perform(ActionContext context) throws CantPerformException {
      setModified();
    }

    public void itemStateChanged(ItemEvent e) {
      // The isFocused() check is to distinguish genuine user-generated
      // events from noise (setting initial values and defaults).
      if(e.getStateChange() == ItemEvent.SELECTED
        && e.getSource() instanceof JComboBox
        && isFocused((JComboBox)e.getSource()))
      {
        setModified();
      }
    }

    private boolean isFocused(JComboBox cb) {
      // Due to the compound nature of JComboBox, the real focus owner
      // is some of its child components, not JComboBox itself.
      for(final Component c : cb.getComponents()) {
        if(c.isFocusOwner()) {
          return true;
        }
      }
      return false;
    }

    public void onChange() {
      setModified();
    }

    public boolean visit(JComponent element) {
      final UIController<?> controller = UIController.CONTROLLER.getClientValue(element);
      if(controller instanceof CCListController2) {
        ((CCListController2)controller).addChangeListener(myLife, this);
        return false;
      }
      return true;
    }
  }
}