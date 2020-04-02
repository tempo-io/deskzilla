package com.almworks.bugzilla.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.completion.CompletingComboBox;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.i18n.ComponentLocalizerVisitor;
import com.almworks.util.ui.UIUtil;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;

import static com.almworks.bugzilla.gui.FieldsPanelBuilder.Width.MAY_BE_HALF_ROW;
import static com.almworks.bugzilla.integration.BugzillaAttribute.*;

/**
 * Shows only configured optional fields.
 */
public class OptionalFieldsController implements UIController<JPanel> {
  private final JPanel myFieldsPanel;
  private final boolean myEdit;

  private final CompletingComboBox<ItemKey> myQaContact = new CompletingComboBox<ItemKey>();
  private final JLabel myQaContactLabel = new JLabel("QA Contact:");
  private final AComboBox<?> myMilestone = new AComboBox<Object>();
  private final JLabel myMilestoneLabel = new JLabel("Milestone:");
  private final JTextField mySeeAlso = new JTextField();
  private final JLabel mySeeAlsoLabel = new JLabel("See Also:");
  private final JTextField myStatusWhiteboard = new JTextField();
  private final JLabel myStatusWhiteboardLabel = new JLabel("Status Whiteboard:");

  private OptionalFieldsController(JPanel fieldsPanel, boolean edit) {
    myFieldsPanel = fieldsPanel;
    myEdit = edit;
    assert fieldsPanel.getLayout() instanceof FormLayout : fieldsPanel.getLayout();
    setupLabels();
    setupNames();
    setupWidths();
  }

  private void setupNames() {
    myQaContact.setName(BugzillaKeys.qaContact.getName());
    myMilestone.setName(BugzillaKeys.milestone.getName());
    mySeeAlso.setName(BugzillaKeys.seeAlso.getName());
    myStatusWhiteboard.setName(BugzillaKeys.statusWhiteboard.getName());
  }

  private void setupLabels() {
    myQaContactLabel.setDisplayedMnemonic('Q');
    myQaContactLabel.setLabelFor(myQaContact);
    myQaContactLabel.setName("qaContact");
    myMilestoneLabel.setDisplayedMnemonic('M');
    myMilestoneLabel.setLabelFor(myMilestone);
    myMilestoneLabel.setName("targetMilestone");
    mySeeAlsoLabel.setLabelFor(mySeeAlso);
    mySeeAlsoLabel.setName("seeAlso");
    myStatusWhiteboardLabel.setDisplayedMnemonic('W');
    myStatusWhiteboardLabel.setLabelFor(myStatusWhiteboard);
    myStatusWhiteboardLabel.setName("statusWhiteboard");
  }

  private void setupWidths() {
    mySeeAlso.setColumns(15);
    myStatusWhiteboard.setColumns(15);
  }

  public static OptionalFieldsController newBug(JPanel fieldsPanel) {
    return new OptionalFieldsController(fieldsPanel, false);
  }

  public static OptionalFieldsController editBug(JPanel fieldsPanel) {
    return new OptionalFieldsController(fieldsPanel, true);
  }

  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull JPanel component) {
    BugzillaContext ctx = BugzillaUtil.getContext(model);
    OptionalFieldsTracker tracker = ctx == null ? null : ctx.getOptionalFieldsTracker();
    FieldsPanelBuilder builder = new FieldsPanelBuilder(myFieldsPanel);
    if (used(QA_CONTACT, tracker))          builder.addField(myQaContact, myQaContactLabel);
    if (used(TARGET_MILESTONE, tracker))    builder.addField(myMilestone, myMilestoneLabel);
    if (myEdit) {
      if (used(SEE_ALSO, tracker))          builder.addField(mySeeAlso, mySeeAlsoLabel, MAY_BE_HALF_ROW);
      if (used(STATUS_WHITEBOARD, tracker)) builder.addField(myStatusWhiteboard, myStatusWhiteboardLabel);
    }

    UIUtil.setDefaultLabelAlignment(myFieldsPanel);
    Aqua.disableMnemonics(myFieldsPanel);

    myFieldsPanel.revalidate();
  }

  public AComboBox<?> getMilestone() {
    return myMilestone;
  }

  public CompletingComboBox<ItemKey> getQaContact() {
    return myQaContact;
  }

  public JTextField getSeeAlso() {
    return mySeeAlso;
  }

  public JTextField getStatusWhiteboard() {
    return myStatusWhiteboard;
  }

  private static boolean used(BugzillaAttribute attr, OptionalFieldsTracker tracker) {
    return !(tracker != null && tracker.isUnused(attr));
  }

  public void localize(ComponentLocalizerVisitor localizer) {
    localizer.processComponent(myMilestoneLabel);
    localizer.processComponent(myQaContactLabel);
    localizer.processComponent(mySeeAlsoLabel);
    localizer.processComponent(myStatusWhiteboardLabel);
  }
}
