package com.almworks.bugzilla.gui.attachments;

import com.almworks.api.actions.AttachScreenshotAction;
import com.almworks.api.actions.BaseAttachFileAction;
import com.almworks.api.application.*;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.bugzilla.gui.view2.BugzillaView2;
import com.almworks.bugzilla.provider.attachments.*;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.engine.gui.AttachmentProperty;
import com.almworks.engine.gui.AttachmentTooltipProvider;
import com.almworks.engine.gui.attachments.BaseAttachmentsController;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.components.ASortedTable;
import com.almworks.util.config.Configuration;
import com.almworks.util.files.FileUtil;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.almworks.bugzilla.provider.attachments.AttachmentInfo.DESCRIPTION;
import static com.almworks.engine.gui.AttachmentProperty.*;

/**
 * Attchment controller for Deskzilla's New/Edit Bug forms.
 */
public class BugzillaAttachmentsController
  extends BaseAttachmentsController<AttachmentInfo, List<AttachmentInfo>>
{
  public static final AnAction ATTACH_FILE = new AttachFile();
  public static final AnAction ATTACH_SCREENSHOT = new AttachScreenshot();
  private static final AnAction DELETE_ATTCHMENT = new DeleteAction();

  private static final List<AttachmentProperty<? super AttachmentInfo, ?>> CREATE_PROPERTIES =
    Arrays.<AttachmentProperty<? super AttachmentInfo, ?>>asList(
      FILE_NAME, DESCRIPTION, MIME_TYPE);

  private static final List<AttachmentProperty<? super AttachmentInfo, ?>> EDIT_PROPERTIES =
    Arrays.<AttachmentProperty<? super AttachmentInfo, ?>>asList(
      ORDER, FILE_NAME, DESCRIPTION, MIME_TYPE, DATE, USER, SIZE);

  private BugzillaAttachmentsController(
    ASortedTable<AttachmentInfo> table, ModelKey<List<AttachmentInfo>> modelKey, Configuration viewConfig)
  {
    super(table, modelKey, viewConfig, AttachmentsLink.attrMaster);
  }

  @Override
  protected List<AttachmentProperty<? super AttachmentInfo, ?>> getAttachmentProperties(boolean creator) {
    return creator ? CREATE_PROPERTIES : EDIT_PROPERTIES;
  }

  @Override
  protected AnAction getDeleteAction() {
    return DELETE_ATTCHMENT;
  }

  @Override
  protected AttachmentTooltipProvider<AttachmentInfo> getTooltipProvider() {
    return BugzillaView2.ATTACHMENT_TOOLTIP_PROVIDER;
  }

  @Override
  protected List<AttachmentInfo> emptyCollection() {
    return Collections15.emptyList();
  }

  public static void install(
    ASortedTable<AttachmentInfo> table, Configuration config, boolean creator,
    ModelKey<List<AttachmentInfo>> key, JComponent... hideComponents)
  {
    final BugzillaAttachmentsController controller = new BugzillaAttachmentsController(table, key, config);
    controller.initialize(creator);
    CONTROLLER.putClientValue(table.toComponent(), controller);
    for(final JComponent component : hideComponents) {
      controller.addHideComponent(component);
    }
  }

  private static class DeleteAction extends BaseDeleteAction<AttachmentInfo, List<AttachmentInfo>> {
    @Override
    protected ModelKey<List<AttachmentInfo>> getKey(ActionContext context) throws CantPerformException {
      return BugzillaKeys.attachments;
    }
  }

  private static abstract class AttachmentAction 
    extends BaseAttachmentAction<AttachmentInfo, List<AttachmentInfo>>
  {
    public AttachmentAction(@Nullable String name, @Nullable Icon icon) {
      super(name, icon);
    }

    @Override
    protected ModelKey<List<AttachmentInfo>> getKey(ActionContext context) throws CantPerformException {
      return BugzillaKeys.attachments;
    }
  }

  private static class AttachFile extends AttachmentAction {
    private AttachFile() {
      super("Attach File", Icons.ACTION_ATTACH_FILE);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      IdActionProxy.setShortcut(context, MetaInfo.ATTACH_FILE);
    }

    @Override
    protected void perform(
      ActionContext context, ItemUiModel model, ModelKey<List<AttachmentInfo>> collectionModelKey)
      throws CantPerformException
    {
      final DialogBuilder builder = context.getSourceObject(DialogManager.ROLE).createBuilder("AttachFile");
      final Configuration config = builder.getConfiguration().getOrCreateSubset("AttachEditor");

      final File[] files = BaseAttachFileAction.askForAttachments(
        context.getComponent(), config, false, BugzillaAttachFileAction.MAX_FILE_LENGTH);

      if(files != null && files.length == 1) {
        final AttachmentEditor editor = new AttachmentEditor(config);
        editor.selectFile(files[0]);

        builder.setTitle("Attach File");
        builder.setModal(false);
        builder.setContent(editor);
        builder.setEmptyCancelAction();
        builder.setOkAction(new AttachFileToModelMapAction(editor, model.getModelMap()));

        builder.showWindow();
      }
    }
  }

  private static class AttachFileToModelMapAction extends SimpleAction {
    private final AttachmentEditor myEditor;
    private final ModelMap myModelMap;

    AttachFileToModelMapAction(AttachmentEditor editor, ModelMap modelMap) {
      super("Attach");
      myEditor = editor;
      myModelMap = modelMap;
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      myEditor.attach(context.getUpdateRequest());
      myEditor.extractAttachmentData();
    }

    protected void doPerform(ActionContext context) throws CantPerformException {
      myEditor.saveMimeTypes();
      AttachmentsModelKey.INSTANCE.createNew(myEditor.extractAttachmentData(), myModelMap);
    }
  }

  private static class AttachScreenshot extends AttachmentAction {
    private AttachScreenshot() {
      super("Attach Screenshot", Icons.ACTION_ATTACH_SCREENSHOT);
    }

    @Override
    protected void customUpdate(UpdateContext context) throws CantPerformException {
      IdActionProxy.setShortcut(context, MetaInfo.ATTACH_SCREENSHOT);
    }

    protected void perform(
      ActionContext context, final ItemUiModel uiModel, final ModelKey<List<AttachmentInfo>> key)
      throws CantPerformException
    {
      AttachScreenshotAction.attach(context, new Procedure2<File, String>() {
        public void invoke(File file, String description) {
          final AttachmentsModelKey attachKey = (AttachmentsModelKey)key;
          final NewAttachment data = new NewAttachment(file, "image/png", file.length(), FileUtil.getSizeString(file.length()), description);
          attachKey.createNew(data, uiModel.getModelMap());
        }
      });
    }
  }
}
