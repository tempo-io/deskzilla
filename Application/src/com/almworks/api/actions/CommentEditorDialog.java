package com.almworks.api.actions;

import com.almworks.api.gui.*;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.*;
import com.almworks.util.components.*;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.config.ConfigAccessors;
import com.almworks.util.config.Configuration;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.*;
import org.almworks.util.Util;
import org.almworks.util.detach.DetachComposite;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;
import util.concurrent.SynchronizedBoolean;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;

public class CommentEditorDialog {
  public static final int COLUMNS = 30;
  public static final int ROWS = 5;

  private final DialogBuilder myBuilder;
  private final Handler myHandler;
  private final JComponent myControls;
  private final String myInitialText;
  private final String myTitle;
  private final boolean myNew;
  private final boolean myDelete;

  private JTextPane myTextEditor;
  private JScrollPane myScrollPane;
  private DetachComposite myLifespan = new DetachComposite();

  private boolean myHandlerCalled = false;
  private boolean myClosingWindow = false;

  private CommentEditorDialog(String title, String initialText, boolean isNew, boolean isDelete, Handler handler,
    JComponent controls, DialogManager dialogManager)
    throws CantPerformException
  {
    myTitle = title;
    myInitialText = initialText;
    myNew = isNew;
    myDelete = isDelete;
    myHandler = handler;
    myControls = controls;

    myBuilder = dialogManager.createBuilder("editComment");
    buildWindow();
  }

  private void buildWindow() {
    createAndConfigureTextEditor();
    createAndConfigureScrollPane();
    createDialogContent();
    configureDialog();
  }

  private void createAndConfigureTextEditor() {
    myTextEditor = new JTextPane();
    SpellCheckManager.attach(myLifespan, myTextEditor);
    ErrorHunt.setEditorPaneText(myTextEditor, myInitialText);
    myTextEditor.setCaretPosition(myTextEditor.getDocument().getLength());
    if(myDelete) {
      myTextEditor.setEditable(false);
      myTextEditor.setBackground(AwtUtil.getPanelBackground());
    }
  }

  private void createAndConfigureScrollPane() {
    myScrollPane = new JScrollPane(myTextEditor);
    final Dimension size = UIUtil.getRelativeDimension(myTextEditor, COLUMNS, ROWS);
    myScrollPane.setMinimumSize(size);
    myScrollPane.setPreferredSize(size);
    UIUtil.configureScrollpaneVerticalOnly(myScrollPane);
  }

  private void createDialogContent() {
    final JPanel content = new JPanel(new BorderLayout());
    content.add(createToolbar(), BorderLayout.NORTH);
    content.add(createEditorPanel(), BorderLayout.CENTER);
    myBuilder.setContent(content);
  }

  private JComponent createToolbar() {
    final ToolbarBuilder builder = ToolbarBuilder.buttonsWithText();

    if(myDelete) {
      builder.addAction(new DeleteAction());
    } else {
      builder.addAction(new UploadAction());
      builder.addAction(new SaveAction());
    }

    final CancelAction cancel = new CancelAction();
    builder.addAction(cancel);
    myBuilder.setCloseConfirmation(cancel);

    final AToolbar toolbar = builder.createHorizontalToolbar();
    Aqua.addSouthBorder(toolbar);
    Aero.addSouthBorder(toolbar);
    return toolbar;
  }

  private JComponent createEditorPanel() {
    final JPanel panel = new JPanel(UIUtil.createBorderLayout());
    panel.add(myScrollPane, BorderLayout.CENTER);
    panel.setBorder(UIUtil.EDITOR_PANEL_BORDER);

    if(myDelete) {
      panel.add(new JLabel("Please confirm deletion of this comment:"), BorderLayout.NORTH);
    }

    if(myControls != null) {
      panel.add(myControls, BorderLayout.SOUTH);
    }

    new DocumentFormAugmentor().augmentForm(Lifespan.NEVER, panel, false);

    return Aqua.isAqua() || Aero.isAero() ? panel : new ScrollPaneBorder(panel);
  }

  private void configureDialog() {
    myBuilder.setTitle(myTitle);
    myBuilder.detachOnDispose(myLifespan);
    myBuilder.setInitialFocusOwner(myTextEditor);
    myBuilder.setBorders(false);
    myBuilder.setBottomLineShown(false);
  }

  public DialogBuilder getBuilder() {
    return myBuilder;
  }

  public JComponent getComponent() {
    return myScrollPane;
  }

  private class UploadAction extends SimpleAction {
    private UploadAction() {
      super(ItemActionUtils.COMMIT_NAME, Icons.ACTION_COMMIT_ARTIFACT);
      setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, ItemActionUtils.COMMIT_TOOLTIP);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      updateCommitAction(context, MainMenu.WorkflowEditor.COMMIT);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      closeAndHandle(context, true, true);
    }
  }

  private class SaveAction extends SimpleAction {
    private SaveAction() {
      super(ItemActionUtils.SAVE_NAME, Icons.ACTION_SAVE);
      setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, ItemActionUtils.SAVE_TOOLTIP);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      updateCommitAction(context, MainMenu.WorkflowEditor.SAVE_DRAFT);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      closeAndHandle(context, true, false);
    }
  }

  private class DeleteAction extends SimpleAction {
    private DeleteAction() {
      super("Delete", Icons.ACTION_DISCARD);
      setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Delete this comment");
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      updateCommitAction(context, MainMenu.WorkflowEditor.COMMIT);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      closeAndHandle(context, true, false);
    }
  }

  private void updateCommitAction(UpdateContext context, String shortcutId) {
    IdActionProxy.setShortcut(context, shortcutId);

    context.updateOnChange(myTextEditor.getDocument());

    final boolean isEmpty = Util.NN(getText()).trim().isEmpty();
    final boolean isChanged = true; // todo
    context.setEnabled(!isEmpty && isChanged);
  }

  private class CancelAction extends SimpleAction {
    private CancelAction() {
      super(ItemActionUtils.CANCEL_NAME, Icons.ACTION_GENERIC_CANCEL_OR_REMOVE);
      setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, ItemActionUtils.CANCEL_TOOLTIP);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      context.putPresentationProperty(PresentationKey.SHORTCUT, Shortcuts.ESCAPE);
    }

    @Override
    protected void doPerform(ActionContext context) throws CantPerformException {
      if(myClosingWindow) {
        return;
      }

      final String oldText = Util.NN(myInitialText).trim();
      final String newText = Util.NN(getText()).trim();

      if(!newText.equals(oldText)) {
        askConfirmation(context);
      } else {
        closeAndHandle(context, false, false);
      }
    }

    private void askConfirmation(ActionContext context) throws CantPerformException {
      context.getSourceObject(WindowController.ROLE).toFront();
      final String what = myNew ? "this comment" : "changes";
      final String message = L.content("Would you like to save " + what + " as a draft?");

      final int askResult = DialogsUtil.askUser(myTextEditor, message, myTitle, DialogsUtil.YES_NO_CANCEL_OPTION);

      if(askResult == DialogsUtil.YES_OPTION) {
        closeAndHandle(context, true, false);
      } else if(askResult == DialogsUtil.NO_OPTION) {
        closeAndHandle(context, false, false);
      } else {
        throw new CantPerformExceptionSilently("Cancelled");
      }
    }
  }

  private void closeAndHandle(ActionContext context, boolean ok, boolean upload) throws CantPerformException {
    myClosingWindow = true;
    if(!myHandlerCalled) {
      myHandlerCalled = true;
      myHandler.onCommentEdited(ok, ok ? getText() : null, upload, this);
    }
    WindowController.CLOSE_ACTION.perform(context);
  }

  private String getText() {
    return DocumentUtil.getDocumentText(myTextEditor.getDocument());
  }

  public static CommentEditorDialog addComment(
    ActionContext context, String bugId, Handler handler, JComponent controls) throws CantPerformException
  {
    return new CommentEditorDialog(createTitle("Add New Comment for", null, bugId), "", true, false, handler, controls,
      context.getSourceObject(DialogManager.ROLE));
  }

  public static CommentEditorDialog deleteComment(
    ActionContext context, String bugID, String commentTitle, String text, Handler handler)
    throws CantPerformException
  {
    DialogManager dialogManager = context.getSourceObject(DialogManager.ROLE);
    return deleteComment(bugID, commentTitle, text, handler, dialogManager);
  }

  public static CommentEditorDialog deleteComment(String bugID, String commentTitle, String text, Handler handler,
    DialogManager dialogManager) throws CantPerformException
  {
    return new CommentEditorDialog(createTitle("Delete Comment", commentTitle, bugID), text, false, true, handler, null,
      dialogManager);
  }

  public static CommentEditorDialog editComment(
    ActionContext context, String bugID, String commentTitle, String initialText, Handler handler, JComponent controls)
    throws CantPerformException
  {
    DialogManager dialogManager = context.getSourceObject(DialogManager.ROLE);
    return editComment(bugID, commentTitle, initialText, handler, controls, dialogManager);
  }

  public static CommentEditorDialog editComment(String bugID, String commentTitle, String initialText, Handler handler,
    JComponent controls, DialogManager dialogManager) throws CantPerformException
  {
    return editCommentWithTitle(createTitle("Edit Comment", commentTitle, bugID), initialText, handler, controls,
      dialogManager);
  }

  public static CommentEditorDialog editCommentWithTitle(
    ActionContext context, String title, String initialText, Handler handler, JComponent controls)
    throws CantPerformException
  {
    return editCommentWithTitle(title, initialText, handler, controls, context.getSourceObject(DialogManager.ROLE));
  }

  public static CommentEditorDialog editCommentWithTitle(String title, String initialText, Handler handler,
    JComponent controls, DialogManager dm) throws CantPerformException
  {
    return new CommentEditorDialog(title, initialText, false, false, handler, controls, dm);
  }

  public static CommentEditorDialog replyComment(
    ActionContext context, String bugID, String initialText, String commentTitle, Handler handler, JComponent controls)
    throws CantPerformException
  {
    return new CommentEditorDialog(createTitle("Reply to", commentTitle, bugID), initialText, true, false, handler, controls,
      context.getSourceObject(DialogManager.ROLE));
  }

  private static String createTitle(String prefix, String commentTitle, String artifactTitle) {
    commentTitle = commentTitle != null ? commentTitle.trim() : "";
    artifactTitle = artifactTitle != null ? artifactTitle.trim() : "";
    if (commentTitle.isEmpty() && artifactTitle.isEmpty()) {
      return prefix;
    }

    final StringBuilder buffer = new StringBuilder(prefix);
    buffer.append(" ");

    if (commentTitle.length() > 0) {
      buffer.append(commentTitle);
    }

    if (artifactTitle.length() > 0) {
      buffer.append(commentTitle.length() > 0 ? " - " : "")
        .append(Local.text(Terms.key_Artifact))
        .append(" ")
        .append(artifactTitle);
    }

    return buffer.toString();
  }

  public static interface Handler {
    void onCommentEdited(boolean success, String commentText, boolean commitImmediately, CommentEditorDialog dialog);
  }

  public static JCheckBox createCommitImmediatelyCheckbox(
    Configuration config, @Nullable final SynchronizedBoolean flagHolder)
  {
    final ConfigAccessors.Bool commitImmediately = ConfigAccessors.bool(config, "commitImmediately", true);

    final JCheckBox checkbox = new JCheckBox(Local.text("app.Comments.UploadImmediately", "Upload immediately"));
    checkbox.setMnemonic('U');

    final boolean flagSet = commitImmediately.getBool();
    if(flagHolder != null) {
      flagHolder.set(flagSet);
    }
    checkbox.setSelected(flagSet);

    checkbox.addChangeListener(new javax.swing.event.ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        final boolean selected = checkbox.isSelected();
        if(flagHolder != null) {
          flagHolder.set(selected);
        }
        commitImmediately.setBool(selected);
      }
    });

    return checkbox;
  }
}
