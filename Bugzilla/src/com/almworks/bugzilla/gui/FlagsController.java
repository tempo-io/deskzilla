package com.almworks.bugzilla.gui;

import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.gui.*;
import com.almworks.bugzilla.gui.flags.edit.EditFlagsAction;
import com.almworks.bugzilla.gui.flags.edit.FlagEditor;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.engine.gui.ExternalAddedSlavesListener;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.util.Env;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.FieldWithMoreButton;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

import static com.almworks.api.gui.MainMenu.ItemEditor.COMMIT;
import static com.almworks.bugzilla.gui.FieldsPanelBuilder.Width.FORCE_FULL_ROW;

public class FlagsController implements UIController<JPanel>, ChangeListener {
  private Lifespan myLife;
  private ModelMap myModel;
  private JLabel myFlagsLabel;
  private FlagsField myFlagsField;

  @Override
  public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model, @NotNull JPanel panel) {
    List<? extends Flag> flags = FlagsModelKey.getAllFlags(model, true);
    if (flagsAreUsed(model, flags)) {
      initialize(lifespan, model);
      addChangeListeners();
      addComponentsTo(panel);
      updateSummaryString();
      ExternalAddedSlavesListener.attach(lifespan, model, panel, FlagsModelKey.MODEL_KEY, Flags.AT_FLAG_MASTER);
    }
  }

  private static boolean flagsAreUsed(ModelMap model, List<? extends Flag> flags) {
    if (!flags.isEmpty()) return true;
    BugzillaConnection connection = BugzillaConnection.getInstance(model);
    if (connection == null) return false;
    return connection.hasFlags();
  }

  private void initialize(Lifespan lifespan, ModelMap model) {
    myLife = lifespan;
    myModel = model;
    myFlagsLabel = makeLabel();
    myFlagsField = makeField();
  }

  private static JLabel makeLabel() {
    final JLabel label = new JLabel();
    NameMnemonic.parseString("&Flags:").setToLabel(label);
    label.setName("flags");
    return label;
  }

  private FlagsField makeField() {
    final FlagsField field = new FlagsField(myLife, myFlagsLabel);
    new DocumentFormAugmentor().augmentForm(myLife, field, true);
    return field;
  }

  private void addChangeListeners() {
    myModel.addAWTChangeListener(myLife, this);
  }

  private void addComponentsTo(JPanel panel) {
    final FieldsPanelBuilder builder = new FieldsPanelBuilder(panel);
    builder.addField(myFlagsField, myFlagsLabel, FORCE_FULL_ROW);
  }

  @Override
  public void onChange() {
    updateSummaryString();
  }

  private void updateSummaryString() {
    if(!myLife.isEnded()) {
      final List<EditableFlag> flags = Collections15.arrayList(FlagsModelKey.getEditFlagState(myModel));
      for (Iterator<EditableFlag> iterator = flags.iterator(); iterator.hasNext();) {
        EditableFlag flag = iterator.next();
        if (flag.isDeleted()) iterator.remove();
      }
      Collections.sort(flags, Flag.ORDER);
      myFlagsField.setText(FlagVersion.getSummaryString(flags));
    }
  }

  private static class FlagsField extends FieldWithMoreButton<ReadOnlyField> implements AnActionListener {
    private final ReadOnlyField myField;
    private final Lifespan myEditorLife;

    private FrameBuilder myCurrentBuilder;

    public FlagsField(Lifespan editorLife, JLabel label) {
      myEditorLife = editorLife;
      label.setLabelFor(myButton);
      myButton.setFocusable(true);
      myField = new ReadOnlyField();
      setField(myField);
      setActionName("Edit Flags");
      setAction(this);
      editorLife.add(new Detach() {
        @Override
        protected void doDetach() throws Exception {
          closeWindow();
        }
      });
    }

    private void closeWindow() {
      final WindowController winCon = getCurrentWindowController();
      if(winCon != null) {
        winCon.close();
      }
    }

    @Nullable
    private WindowController getCurrentWindowController() {
      if(myCurrentBuilder == null) {
        return null;
      }
      return myCurrentBuilder.getWindowContainer().getActor(WindowController.ROLE);
    }

    public void setText(String text) {
      myField.setText(text);
    }

    @Override
    public void perform(ActionContext context) throws CantPerformException {
      if(myEditorLife.isEnded()) {
        return;
      }
      if(myCurrentBuilder == null || !activateEditor()) {
        showNewEditor(context);
      }
    }

    private boolean activateEditor() throws CantPerformException {
      final WindowController winCon = getCurrentWindowController();
      if(winCon != null) {
        winCon.activate();
        return true;
      }
      assert false;
      return false;
    }

    private void showNewEditor(ActionContext context) throws CantPerformException {
      final WindowManager winMan = context.getSourceObject(WindowManager.ROLE);
      final FrameBuilder builder = winMan.createFrame("editFlags");
      final ItemUiModelImpl uiModel = context.getSourceObject(ItemUiModelImpl.ROLE);
      final FlagEditor editor = FlagEditor.create(uiModel, false);
      builder.setTitle(EditFlagsAction.getTitle(uiModel));
      builder.setContent(editor);
      builder.setWindowPositioner(new WindowUtil.OwnedPositioner(
        myField, new UIUtil.IndependentPositioner(UIUtil.ALIGN_START, new UIUtil.AfterOrBefore(UIUtil.GAP))));

      myCurrentBuilder = builder;
      builder.showWindow(new Detach() {
        @Override
        protected void doDetach() throws Exception {
          myCurrentBuilder = null;
        }
      });

      if(!Env.isMac()) {
        setupCommitShortcut(editor, context);
      }
    }

    private void setupCommitShortcut(FlagEditor editor, ActionContext context) {
      final ScopedKeyStroke shortcut = IdActionProxy.getShortcut(context, COMMIT);
      if(shortcut == null) {
        return;
      }
      
      final JComponent comp = editor.getComponent();
      comp.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(shortcut.getKeyStroke(), COMMIT);
      comp.getActionMap().put(COMMIT, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          closeWindow();
        }
      });
    }
  }

  private static class ReadOnlyField extends JTextField {
    private final Color myNormalFg = getForeground();
    private final Color myEmptyFg = ColorUtil.between(getForeground(), getBackground(), 0.5f);

    public ReadOnlyField() {
      setEditable(false);
      setFocusable(false);
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          setFocusable(true);
          requestFocusInWindow();
          selectAll();
        }
      });
      addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          setFocusable(false);
          select(0, 0);
        }
      });
    }

    @Override
    public void setText(String t) {
      if(t == null || t.isEmpty()) {
        setForeground(myEmptyFg);
        super.setText("<None>");
      } else {
        setForeground(myNormalFg);
        super.setText(t);
      }
    }
  }
}
