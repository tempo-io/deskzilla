package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.ADateField;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.i18n.ComponentLocalizerVisitor;
import com.almworks.util.i18n.NameBasedComponentLocalizer;
import com.almworks.util.model.ValueModel;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.*;
import com.almworks.util.ui.swing.DocumentUtil;
import com.jgoodies.forms.layout.*;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * This controller is responsible for adding time tracking fields
 * to the New Bug and Edit Bug forms if the user can track time in Bugzilla.
 */
public class TimeFieldsController implements UIController<JPanel> {
  /**
   * Client property key to distinguish components added by {@code TimeFieldsController}.
   */
  private static final ComponentProperty<Object> CONTROLLED = ComponentProperty.createProperty("TimeTrackingField");

  /**
   * The minimum allowed value for Original and Remaining estimates.
   */
  private static final BigDecimal MIN_ESTIMATE = new BigDecimal("0");

  /**
   * The maximum allowed value for Original and Remaining estimates.
   */
  private static final BigDecimal MAX_ESTIMATE = new BigDecimal("99999.99");

  /**
   * The minimum allowed value for additional time spent.
   */
  private static final BigDecimal MIN_SPENT = new BigDecimal("-999.99");

  /**
   * The maximum allowed value for additional time spent.
   */
  private static final BigDecimal MAX_SPENT = new BigDecimal("999.99");

  /**
   * The maximum allowed value scale.
   */
  private static final int MAX_SCALE = 2;

  private ConnectContext myContext;
  private DocumentFormAugmentor myAugmentor;
  protected JPanel myPanel;
  protected FormLayout myLayout;
  private CellConstraints myConstraints;

  /**
   * Mark the given component as added by a {@code TimeFieldsController}.
   *
   * @param c The component.
   */
  protected static void markAsControlled(JComponent c) {
    CONTROLLED.putClientValue(c, Boolean.TRUE);
  }

  /**
   * Checks whether the given component was added
   * by a {@code TimeFieldsController}.
   *
   * @param c The component.
   * @return {@code true} if {@code c} was added by a controller.
   */
  protected static boolean isControlled(Component c) {
    return c instanceof JComponent && CONTROLLED.getClientValue((JComponent) c) != null;
  }

  /**
   * Adds a new row to the panel layout.
   *
   * @param layout The panel layout manager.
   */
  protected static void addRow(FormLayout layout) {
    layout.appendRow(new RowSpec("4dlu"));
    layout.appendRow(new RowSpec("d"));
    layout.addGroupedRow(layout.getRowCount());
  }

  /**
   * Determines whether the time tracking permission is
   * granted in the given context.
   *
   * @param context The {@code ConnectContext}.
   * @return Whether time tracking permission is granted, never {@code null}.
   */
  @NotNull
  private static Boolean isTimeTrackingAllowed(ConnectContext context) {
    final Connection conn = context.getConnection();

    if (!(conn instanceof BugzillaConnection)) {
      assert false : conn;
      return Boolean.FALSE;
    }

    final PermissionTracker pt = ((BugzillaContext) ((BugzillaConnection) conn).getContext()).getPermissionTracker();
    return Util.NN(pt.isTimeTrackingAllowed(), Boolean.FALSE);
  }

  /**
   * Remove the fields added by a {@code TimeFieldsController}
   * from the given panel (if any).
   *
   * @param panel  The panel containing bug fields.
   * @param layout The panel layout manager.
   */
  private static void removeControlledFields(JPanel panel, FormLayout layout) {
    int minRow = Integer.MAX_VALUE;

    for (final Component c : panel.getComponents()) {
      if (isControlled(c)) {
        final CellConstraints cc = layout.getConstraints(c);
        minRow = Math.min(minRow, cc.gridY);
        panel.remove(c);
      }
    }

    if (minRow < Integer.MAX_VALUE) {
      final int lastRow = minRow - 2;
      UIUtil.removeExcessRowsFromFormLayout(layout, lastRow);
    }
  }

  /**
   * Final cosmetic adjustments of the panel.
   *
   * @param panel The panel containing bug fields.
   */
  private static void makePanelPretty(JPanel panel) {
    UIUtil.setDefaultLabelAlignment(panel);
    Aqua.disableMnemonics(panel);
  }

  /**
   * The flag keeping this controller's "time tracking allowed" state.
   * Initially {@code null} to always trigger a field rebuild.
   */
  private Boolean myCurrentTrackingAllowed = null;

  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull JPanel panel) {
    final ConnectContext context = new ConnectContext(lifespan, model);

    final Boolean trackingAllowed = isTimeTrackingAllowed(context);
    if (!trackingAllowed.equals(myCurrentTrackingAllowed)) {
      myCurrentTrackingAllowed = trackingAllowed;

      if (!(panel.getLayout() instanceof FormLayout)) {
        assert false : panel.getLayout();
        return;
      }

      final FormLayout layout = (FormLayout) panel.getLayout();

      if (trackingAllowed) {
        insertConrolledFields(context, panel, layout);
      } else {
        removeControlledFields(panel, layout);
      }

      makePanelPretty(panel);
    }
  }

  /**
   * Override this method to insert time tracking fields
   * as appropriate. Don't forget to call {@link #markAsControlled(javax.swing.JComponent)}
   * for anything you add.
   *
   * @param context The {@code ConnectContext}.
   * @param panel   The panel containing bug fields.
   * @param layout  The panel layout manager.
   */
  protected void insertConrolledFields(ConnectContext context, JPanel panel, FormLayout layout) {
    myAugmentor = new DocumentFormAugmentor();
    myContext = context;
    myPanel = panel;
    myLayout = layout;
    myConstraints = new CellConstraints(1, layout.getRowCount(), CellConstraints.FILL, CellConstraints.CENTER);

    doInsertFields(context);

    NameBasedComponentLocalizer localizer = new NameBasedComponentLocalizer("bz.form.label.", JLabel.class);
    UIUtil.updateComponents(myPanel, new ComponentLocalizerVisitor(localizer));

    myAugmentor = null;
    myContext = null;
    myPanel = null;
    myLayout = null;
    myConstraints = null;
  }

  /**
   * Override this method to insert fields using {@link #newRow()},
   * {@link #add(String, javax.swing.JComponent, boolean)} and
   * {@link #listen(javax.swing.text.JTextComponent, boolean, com.almworks.util.commons.Procedure)}.
   *
   * @param context The {@code ConnectContext}.
   */
  protected void doInsertFields(ConnectContext context) {
    assert false;
  }

  /**
   * Creates a new text field.
   *
   * @param name     Name for the field.
   * @param editable Whether the field is editable.
   * @return The new field.
   */
  protected static JTextField text(String name, boolean editable) {
    final JTextField field = new JTextField();
    field.setColumns(5);
    if (name != null) {
      field.setName(name);
    }
    field.setEditable(editable);
    field.setFocusable(editable);
    if (!editable && !Aqua.isAqua()) {
      field.setForeground(UIManager.getColor("TextField.inactiveForeground"));
    }
    return field;
  }

  /**
   * Creates a new date field.
   *
   * @param name Name for the field.
   * @param key  Model key for the field.
   * @return The new field.
   */
  protected static ADateField date(String name, ModelKey<Date> key) {
    final ADateField field = new ADateField();
    field.setColumns(5);
    if (name != null) {
      field.setName(name);
    }
    BugzillaFormUtils.setupDateField(field, key);
    return field;
  }

  /**
   * Adds a new row to the panel layout.
   */
  protected void newRow() {
    addRow(myLayout);
    myConstraints.gridY += 2;
  }

  /**
   * Adds a thin spacer row to the panel layout.
   */
  protected void space() {
    myLayout.appendRow(new RowSpec("5dlu"));
    myConstraints.gridY++;
  }

  /**
   * Adds a label and a field to the panel.
   *
   * @param title The title (label text) for the field.
   * @param field The field.
   * @param first Whether the field is first or second in a row.
   * @param <C>   Field type.
   * @return {@code field}.
   */
  protected <C extends JComponent> C add(String title, String labelName, C field, boolean first) {
    myConstraints.gridX = first ? 1 : 5;

    final JLabel label = new JLabel(title);
    label.setName(labelName);
    label.setLabelFor(field);
    markAsControlled(label);
    myPanel.add(label, myConstraints);

    markAsControlled(field);
    myAugmentor.augmentForm(myContext.getLife(), field, true);
    myConstraints.gridX += 2;
    myPanel.add(field, myConstraints);

    return field;
  }

  /**
   * Augment components with this controller's {@link DocumentFormAugmentor}.
   *
   * @param comps The components.
   */
  protected void augment(JComponent... comps) {
    for (final JComponent comp : comps) {
      myAugmentor.augmentForm(myContext.getLife(), comp, true);
    }
  }

  /**
   * Attaches a contents change listener to a field. The listener
   * survives changnig the field's Document property.
   *
   * @param field The field.
   * @param parse Whether the field's contents should be parsed.
   * @param proc  The procedure that receives sussessful parse results or {@code null} if parsing is off.
   * @param min   The minimum allowed value ({@code null} for no check).
   * @param max   The maximum allowed value ({@code null} for no check).
   */
  protected void listen(@NotNull final JTextComponent field, final boolean parse,
    @Nullable final Procedure<BigDecimal> proc, @Nullable final BigDecimal min, @Nullable final BigDecimal max)
  {
    if (!parse && proc == null) {
      return;
    }

    class MyListener extends DocumentAdapter implements PropertyChangeListener {
      final Color normalFg = field.getForeground();
      final ConnectContext context = myContext;

      protected void documentChanged(DocumentEvent e) {
        if (parse) {
          final BigDecimal val = parse(field, min, max);
          if (val == null) {
            if (field.getText().trim().length() == 0) {
              field.setForeground(normalFg);
            } else {
              field.setForeground(GlobalColors.ERROR_COLOR);
            }
          } else {
            field.setForeground(normalFg);
          }
          if (proc != null) {
            proc.invoke(val);
          }
        } else {
          if (proc != null) {
            proc.invoke(null);
          }
        }
      }

      public void propertyChange(PropertyChangeEvent evt) {
        final Object oldDoc = evt.getOldValue();
        if (oldDoc instanceof Document) {
          ((Document) oldDoc).removeDocumentListener(this);
        }

        final Object newDoc = evt.getNewValue();
        if (newDoc instanceof Document) {
          DocumentUtil.addListener(context.getLife(), (Document) newDoc, this);
        }
      }
    }

    final MyListener listener = new MyListener();
    field.addPropertyChangeListener("document", listener);
    DocumentUtil.addListener(myContext.getLife(), field.getDocument(), listener);
  }

  /**
   * Parse a field's contents to get a {@code BigDecimal} from it.
   *
   * @param field The field.
   * @return The parsed {@code BigDecimal} or {@code null} if the field
   *         is empty or its contents cannot be parsed.
   */
  protected BigDecimal parse(@NotNull JTextComponent field) {
    if (field.getText().trim().length() == 0) {
      return null;
    } else {
      try {
        final BigDecimal val = new BigDecimal(field.getText());
        return val;
      } catch (NumberFormatException nfe) {
        return null;
      }
    }
  }

  /**
   * Check whether a {@code BigDecimal} value is within an
   * acceptable range and its scale is no more than {@link #MAX_SCALE}.
   *
   * @param val The value.
   * @param min The minimum allowed value ({@code null} for no check).
   * @param max The maximum allowed value ({@code null} for no check).
   * @return {@code val}, if it is acceptable, {@code null} otherwise.
   */
  protected BigDecimal check(@Nullable BigDecimal val, @Nullable BigDecimal min, @Nullable BigDecimal max) {
    if (val == null) {
      return null;
    }

    if (val.scale() > MAX_SCALE) {
      return null;
    }

    if (min != null && val.compareTo(min) < 0) {
      return null;
    }

    if (max != null && val.compareTo(max) > 0) {
      return null;
    }

    return val;
  }

  /**
   * A convenient invocation of {@link #check(java.math.BigDecimal, java.math.BigDecimal, java.math.BigDecimal)}
   * on the result of {@link #parse(javax.swing.text.JTextComponent)}.
   *
   * @param field The text component.
   * @param min   The minimum allowed value ({@code null} for no check).
   * @param max   The maximum allowed value ({@code null} for no check).
   * @return The parsed {@code BigDecimal} or {@code null} if the field
   *         is empty, its contents cannot be parsed, or its value is unacceptable.
   */
  protected BigDecimal parse(@NotNull JTextComponent field, @Nullable BigDecimal min, @Nullable BigDecimal max) {
    return check(parse(field), min, max);
  }

  /**
   * The {@code TimeFieldController} for the New Bug dialog.
   * Adds Estimate and Deadline fields only.
   */
  public static class NewBug extends TimeFieldsController {
    protected void doInsertFields(ConnectContext context) {
      newRow();
      add("Estimated Time:", "estimatedTime", text("estimated_time", true), true);
      add("Deadline:", "deadline", date("deadline", BugzillaKeys.deadline), false);
    }
  }


  /**
   * The {@code TimeFieldController} for the Edit Bug dialog.
   * Adds all time tracking fields.
   */
  public static class EditBug extends TimeFieldsController {
    private static final BigDecimal ZERO = new BigDecimal("0");

    /**
     * This model is used to drive the {@code NewCommentController}.
     */
    private final ValueModel<BigDecimal> myAddedHoursModel;

    public EditBug(ValueModel<BigDecimal> addedHoursModel) {
      super();
      myAddedHoursModel = addedHoursModel;
    }

    protected void doInsertFields(ConnectContext context) {
      final JPanel panel = new JPanel(
        new FormLayout("p:g(0.15), p, p:g(0.15), 4dlu, p, 4dlu, p:g(0.4), 4dlu, p, 4dlu, p:g(0.15), p, p:g(0.15)",
          "pref, 4dlu, pref"));
//      final JPanel panel = new JPanel(new FormLayout(
//        "d:g(0.3), 4dlu, d, 4dlu, d:g(0.2), d, d:g(0.2), 4dlu, d, 4dlu, d:g(0.3)",
//        "pref, 4dlu, pref"));

      final CellConstraints cc = new CellConstraints();

      final JTextField origEstField = text("estimated_time", true);
      final JTextField currEstField = text(null, false);

      final JTextField hrsField = text("total_actual_time", false);
      final JTextField addField = text(null, true);

      final JTextField remField = text("remaining_time", true);
      final ADateField ddlField = date("deadline", BugzillaKeys.deadline);

      final JTextField gainField = text(null, false);
      final JTextField cmplField = text(null, false);

      panel.add(origEstField, cc.xywh(1, 1, 3, 1));
      panel.add(makeLabel("Current Est.:", "currentEstimate"), cc.xy(5, 1));
      panel.add(currEstField, cc.xy(7, 1));
      panel.add(makeLabel("Deadline:", "deadline"), cc.xy(9, 1));
      panel.add(ddlField, cc.xywh(11, 1, 3, 1));

      panel.add(hrsField, cc.xy(1, 3));
      panel.add(new JLabel(" + "), cc.xy(2, 3));
      panel.add(addField, cc.xy(3, 3));
      panel.add(makeLabel("Hours &Left:", "hoursLeft", remField), cc.xy(5, 3));
      panel.add(remField, cc.xy(7, 3));
      panel.add(makeLabel("Complete:", "complete"), cc.xy(9, 3));
      panel.add(cmplField, cc.xy(11, 3));
      panel.add(new JLabel("  Gain: "), cc.xy(12, 3));
      panel.add(gainField, cc.xy(13, 3));

//      panel.add(origEstField, cc.xy(1, 1));
//      panel.add(makeLabel("Hours Wor&ked:", addField), cc.xy(3, 1));
//      panel.add(hrsField, cc.xy(5, 1));
//      panel.add(new JLabel("  Add: "), cc.xy(6, 1));
//      panel.add(addField, cc.xy(7, 1));
//      panel.add(makeLabel("Hours &Left:", remField), cc.xy(9, 1));
//      panel.add(remField, cc.xy(11, 1));
//
//      panel.add(currEstField, cc.xy(1, 3));
//      panel.add(new JLabel("Complete:"), cc.xy(3, 3));
//      panel.add(cmplField, cc.xy(5, 3));
//      panel.add(new JLabel("  Gain: "), cc.xy(6, 3));
//      panel.add(gainField, cc.xy(7, 3));
//      panel.add(new JLabel("Deadline:"), cc.xy(9, 3));
//      panel.add(ddlField, cc.xy(11, 3));

      DocumentFormAugmentor.DO_NOT_AUGMENT.putClientValue(ddlField, Boolean.TRUE);
      augment(panel, origEstField, addField, remField, ddlField);

      space();
      newRow();
      newRow();

      final JLabel estLabel = makeLabel("Ori&ginal Estimate:", "originalEstimate", origEstField);
      markAsControlled(estLabel);
      myPanel.add(estLabel, cc.xy(1, myLayout.getRowCount() - 2));

      final JLabel hrsLabel = makeLabel("Ho&urs Worked:", "hoursWorked", addField);
      markAsControlled(hrsLabel);
      myPanel.add(hrsLabel, cc.xy(1, myLayout.getRowCount()));

//      final JLabel currEstLabel = new JLabel("Current Estimate:");
//      markAsControlled(currEstLabel);
//      myPanel.add(currEstLabel, cc.xy(1, myLayout.getRowCount()));

      markAsControlled(panel);
      myPanel.add(panel, cc.xywh(3, myLayout.getRowCount() - 2, 5, 3));

      space();

      final BigDecimal estTime = context.getValue(BugzillaKeys.estimatedTime);
      final BigDecimal wrkTime = context.getValue(BugzillaKeys.workedTime);
      final BigDecimal remTime = context.getValue(BugzillaKeys.remainingTime);

      if (wrkTime != null || remTime != null) {
        currEstField.setText(Util.NN(wrkTime, ZERO).add(Util.NN(remTime, ZERO)).toString());
      }

      if (estTime != null || wrkTime != null || remTime != null) {
        final BigDecimal currEst = Util.NN(wrkTime, ZERO).add(Util.NN(remTime, ZERO));
        gainField.setText(Util.NN(estTime, ZERO).subtract(currEst).toString());
        if (currEst.compareTo(ZERO) != 0) {
          cmplField.setText(
            Util.NN(wrkTime, ZERO).divide(currEst, 2, RoundingMode.DOWN).movePointRight(2).toString() + "%");
        }
      }

      final Procedure<BigDecimal> remTimeUpdater = new Procedure<BigDecimal>() {
        public void invoke(BigDecimal arg) {
          if (arg != null && arg.compareTo(ZERO) == 0) {
            arg = null;
          }

          myAddedHoursModel.setValue(arg);

          if (remTime != null) {
            BigDecimal newRemain = remTime.subtract(Util.NN(arg, ZERO));
            if (newRemain.compareTo(ZERO) < 0) {
              newRemain = ZERO;
            }
            remField.setText(newRemain.toString());
          }
        }
      };
      listen(addField, true, remTimeUpdater, MIN_SPENT, MAX_SPENT);

      final Procedure<BigDecimal> currEstUpdater = new Procedure<BigDecimal>() {
        public void invoke(BigDecimal arg) {
          final BigDecimal currEst =
            Util.NN(wrkTime, ZERO).add(Util.NN(parseAdd(addField), ZERO)).add(Util.NN(parseEst(remField), ZERO));
          currEstField.setText(currEst.toString());
        }
      };
      listen(addField, true, currEstUpdater, MIN_SPENT, MAX_SPENT);
      listen(remField, true, currEstUpdater, MIN_ESTIMATE, MAX_ESTIMATE);

      final Procedure<BigDecimal> gainUpdater = new Procedure<BigDecimal>() {
        public void invoke(BigDecimal arg) {
          final BigDecimal gain = Util.NN(parseEst(origEstField), ZERO).subtract(Util.NN(parseEst(currEstField), ZERO));
          gainField.setText(gain.toString());
        }
      };
      listen(origEstField, true, gainUpdater, MIN_ESTIMATE, MAX_ESTIMATE);
      listen(currEstField, true, gainUpdater, null, null);

      final Procedure<BigDecimal> cmplUpdater = new Procedure<BigDecimal>() {
        public void invoke(BigDecimal arg) {
          final BigDecimal currEst = Util.NN(parseEst(currEstField), ZERO);
          if (currEst.compareTo(ZERO) != 0) {
            final BigDecimal cmpl = Util.NN(wrkTime, ZERO)
              .add(Util.NN(parseAdd(addField), ZERO))
              .divide(currEst, 2, RoundingMode.DOWN)
              .movePointRight(2);
            cmplField.setText(cmpl.toString() + "%");
          } else {
            cmplField.setText("");
          }
        }
      };
      listen(addField, true, cmplUpdater, MIN_SPENT, MAX_SPENT);
      listen(currEstField, true, cmplUpdater, null, null);
    }

    private static JLabel makeLabel(String text, String labelName) {
      return makeLabel(text, labelName, null);
    }
    
    private static JLabel makeLabel(String text, String labelName, @Nullable JComponent field) {
      final JLabel label = new JLabel();
      label.setName(labelName);
      NameMnemonic.parseString(text).setToLabel(label);
      if (field != null) label.setLabelFor(field);
      return label;
    }
    
    private BigDecimal parseEst(JTextComponent field) {
      return parse(field, MIN_ESTIMATE, MAX_ESTIMATE);
    }

    private BigDecimal parseAdd(JTextComponent field) {
      return parse(field, MIN_SPENT, MAX_SPENT);
    }
  }
}
