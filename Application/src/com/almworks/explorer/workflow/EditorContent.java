package com.almworks.explorer.workflow;

import com.almworks.actions.ConfirmEditDialog;
import com.almworks.api.actions.*;
import com.almworks.api.application.MetaInfo;
import com.almworks.api.application.VerifierManager;
import com.almworks.api.edit.EditLifecycle;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.*;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.items.sync.EditCommit;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.*;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.AToolbar;
import com.almworks.util.components.ToolbarBuilder;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.plaf.patches.Aero;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.IconHandle;
import com.almworks.util.images.Icons;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.swing.Shortcuts;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifecycle;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

import static org.almworks.util.Collections15.arrayList;

/**
 * @author dyoma
 */
public class EditorContent implements UIComponentWrapper2 {
  static final DataRole<EditorContent> ROLE = DataRole.createRole(EditorContent.class);

  private final JPanel myWholePanel = new JPanel(new BorderLayout());
  private final JLabel myReportingLabel = new JLabel();
  private final Collection<ItemUiModelImpl> myWrappers;
  private final EditLifecycle myEditLife;
  private final Detach myDetach;

  public EditorContent(ItemEditorUi content, Collection<ItemUiModelImpl> wrappers, EditLifecycle editLife) {
    myWrappers = wrappers;
    myEditLife = editLife;
    myDetach = content.getDetach();
    myWholePanel.add(content.getComponent(), BorderLayout.CENTER);
    myReportingLabel.setForeground(GlobalColors.ERROR_COLOR);

    ConstProvider.addRoleValue(myWholePanel, ItemEditorUi.ROLE, content);
    ConstProvider.addRoleValue(myWholePanel, ROLE, this);

    final ToolbarBuilder builder = new ToolbarBuilder();
    builder.addAction(MainMenu.WorkflowEditor.COMMIT);
    builder.addAction(MainMenu.WorkflowEditor.SAVE_DRAFT);
    builder.addAction(MainMenu.WorkflowEditor.DISCARD);

    final AToolbar toolbar = builder.createToolbar(InlineLayout.HORISONTAL);
    Aqua.addSouthBorder(toolbar);
    Aero.addSouthBorder(toolbar);
    myWholePanel.add(toolbar, BorderLayout.NORTH);

    myReportingLabel.setBackground(AwtUtil.getPanelBackground());
    myReportingLabel.setOpaque(true);
    myReportingLabel.setBorder(UIUtil.BORDER_5);
    Aqua.addNorthBorder(myReportingLabel);
    Aero.addNorthBorder(myReportingLabel);
    myWholePanel.add(myReportingLabel, BorderLayout.SOUTH);
  }

  private void saveChanges(ActionContext context, final boolean upload) throws CantPerformException {
    ItemEditorUi editor = context.getSourceObject(ItemEditorUi.ROLE);
    EditCommit commit;
    // todo :verification: comment this line
    commit = createCommitProcedure(myWrappers, editor, context, upload);
    // todo :verification: uncomment following 2 lines
//    Pair<List<ItemUiModelImpl>, Boolean> models_upload = collectChangesAndVerify(context, editor, upload);
//    commit = createCommitProcedure(models_upload.getFirst(), models_upload.getSecond());
    myEditLife.commit(context, commit);
  }


  public static EditCommit createCommitProcedure(Collection<ItemUiModelImpl> items, ItemEditorUi editor, ActionContext context, final boolean upload) throws CantPerformException {
    List<ItemUiModelImpl> modelsToCommit = collectChangesNoVerification(items, editor);
    return createCommitProcedure(modelsToCommit, context.getSourceObject(DialogManager.ROLE), upload);
  }

  private static EditCommit createCommitProcedure(final List<ItemUiModelImpl> modelsToCommit, DialogManager dialogMan, boolean upload) {
    EditCommit commit = CommitModel.create(modelsToCommit, dialogMan);
    if (!upload) return commit;
    return AggregatingEditCommit.toAggregating(commit).addProcedure(null, UploadOnSuccess.create(modelsToCommit));
  }

  private static List<ItemUiModelImpl> collectChangesNoVerification(Collection<ItemUiModelImpl> items, ItemEditorUi editor) throws CantPerformExceptionExplained {
    final List<ItemUiModelImpl> modelsToCommit = arrayList(items.size());
    for (ItemUiModelImpl model : items) {
      editor.copyValues(model);
      if (model.isChanged()) {
        modelsToCommit.add(model);
      }
    }
    return modelsToCommit;
  }

  // this method will be used to verify edit
  @SuppressWarnings({"UnusedDeclaration"})
  private Pair<List<ItemUiModelImpl>, Boolean> collectChangesAndVerify(ActionContext context, ItemEditorUi editor, boolean upload) throws CantPerformException {
    StringBuilder errors = null;
    String pre = "";
    boolean many = myWrappers.size() > 1;
    int nErr = 0;
    List<ItemUiModelImpl> models = arrayList(myWrappers.size());
    for (ItemUiModelImpl model : myWrappers) {
      MetaInfo meta = model.getMetaInfo();
      if (meta == null) { assert false; continue; }
      Lifecycle verificationLife = new Lifecycle();
      try {
        StringBuilder curErrors = new StringBuilder();
        VerifierManager verifierManager = meta.getVerifierManager();
        if (upload) {
          verifierManager.setVerifyContext(verificationLife.lifespan(), model, collectErrors(curErrors));
        } else {
          verifierManager.setVerifyEditContext(verificationLife.lifespan(), model, collectErrors(curErrors));
        }

        // verification takes place here, in edit primitives
        editor.copyValues(model);
        // verify the remaining keys
        verifierManager.callUnusedVerifiers(model, model.getLastDBValues(), model.takeSnapshot());
        if (curErrors.length() > 0) {
          if (many) {
            ++nErr;
            errors = appendItemRepresentation(errors, model, pre, nErr).append(":\n");
            pre = "\n\n";
          }
          errors = TextUtil.append(errors, curErrors);
        } else if (model.isChanged()) {
          models.add(model);
        }
      } finally {
        verificationLife.dispose();
      }
    }

    if (errors != null) {
      DialogBuilder dlg = context.getSourceObject(DialogManager.ROLE).createBuilder("WorkflowEditor.confirmEdit");
      ConfirmEditDialog.Result res = ConfirmEditDialog.show(dlg, upload, errors.toString(), many);
      if (res.isContinueEdit()) {
        throw new CantPerformExceptionSilently("User chose to continue editing");
      }
      upload &= res.isUpload();
    }

    return Pair.create(models, upload);
  }

  private static StringBuilder appendItemRepresentation(StringBuilder sb, ItemUiModelImpl model, String pre, int nErr) {
    Connection connection = model.getConnection();
    String representation = connection != null ? connection.getDisplayableItemId(model) : null;
    if (representation == null)
      representation = model.getItemUrl();
    if (representation != null) {
      //noinspection ConstantConditions
      sb = TextUtil.append(sb, pre).append(representation);
    } else {
      //noinspection ConstantConditions
      sb = TextUtil.append(sb, pre).append('(').append(Local.parse(Terms.ref_artifact)).append(" #").append(nErr).append(')');
    }
    return sb;
  }

  private static Procedure<String> collectErrors(final StringBuilder errors) {
    final String[] sep = new String[] { "" };
    return new Procedure<String>() {
      @Override
      public void invoke(String error) {
        errors.append(sep[0]).append(error);
        sep[0] = "\n";
      }
    };
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  @Deprecated
  public void dispose() {
    myDetach.detach();
  }

  public Detach getDetach() {
    return myDetach;
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.WorkflowEditor.COMMIT,
      new FinishEditAction(
        ItemActionUtils.COMMIT_NAME, Icons.ACTION_COMMIT_ARTIFACT,
        L.tooltip(ItemActionUtils.COMMIT_TOOLTIP),
        true, true));

    registry.registerAction(MainMenu.WorkflowEditor.SAVE_DRAFT,
      new FinishEditAction(
        ItemActionUtils.SAVE_NAME, Icons.ACTION_SAVE,
        L.tooltip(ItemActionUtils.SAVE_TOOLTIP),
        false, false));

    registry.registerAction(MainMenu.WorkflowEditor.DISCARD,
      new SimpleAction(L.actionName(ItemActionUtils.CANCEL_NAME), Icons.ACTION_GENERIC_CANCEL_OR_REMOVE) {
        {
          setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip(ItemActionUtils.CANCEL_TOOLTIP));
          setDefaultPresentation(PresentationKey.SHORTCUT, Shortcuts.ESCAPE);
          setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
        }

        protected void customUpdate(UpdateContext context) {}

        protected void doPerform(ActionContext context) throws CantPerformException {
          WindowController.CLOSE_ACTION.perform(context);
        }
      });

  }

  private static class FinishEditAction extends SimpleAction {
    private final boolean myReportToLabel;
    private final boolean myUpload;

    public FinishEditAction(String name, IconHandle icon, String tooltip, boolean reportToLabel, boolean upload) {
      super(name, icon);
      setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, tooltip);
      watchRole(ItemEditorUi.ROLE);
      watchModifiableRole(EditLifecycle.MODIFIABLE);
      myReportToLabel = reportToLabel;
      myUpload = upload;
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      context.getSourceObject(EditLifecycle.ROLE).checkCommitAction();

      ItemEditorUi editor = context.getSourceObject(ItemEditorUi.ROLE);
      context.updateOnChange(editor.getModifiable());

      final String problem = editor.getSaveProblem();
      context.setEnabled(problem == null);

      if (myReportToLabel) {
        final JLabel label = context.getSourceObject(ROLE).myReportingLabel;
        label.setText(problem);
        label.setVisible(problem != null);
      }
    }

    protected void doPerform(ActionContext context) throws CantPerformException {
      context.getSourceObject(ROLE).saveChanges(context, myUpload);
    }
  }
}