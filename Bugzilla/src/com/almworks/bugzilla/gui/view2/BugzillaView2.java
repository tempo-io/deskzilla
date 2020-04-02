package com.almworks.bugzilla.gui.view2;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.*;
import com.almworks.api.application.viewer.textdecorator.TextDecoratorRegistry;
import com.almworks.api.download.DownloadedFile;
import com.almworks.api.explorer.util.ElementViewerImpl;
import com.almworks.bugzilla.gui.BugzillaFormUtils;
import com.almworks.bugzilla.gui.comments.*;
import com.almworks.bugzilla.gui.flags.BugFlagsFormlet;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.attachments.AttachmentInfo;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.engine.gui.*;
import com.almworks.engine.gui.attachments.AttachmentsFormlet;
import com.almworks.util.L;
import com.almworks.util.collections.Containers;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.*;
import com.almworks.util.components.layout.WidthDrivenColumn;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.config.Configuration;
import com.almworks.util.exec.Context;
import com.almworks.util.files.ExternalBrowser;
import com.almworks.util.images.Icons;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.ui.ElementViewer;
import com.almworks.util.ui.actions.*;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.List;
import java.util.*;

import static com.almworks.engine.gui.AttachmentProperty.*;

public class BugzillaView2 extends CommonIssueViewer {
  private static final Color PRIVATE_COMMENT_FOREGROUND = new Color(0x8b0000);

  public static final MyAttachmentTooltipProvider ATTACHMENT_TOOLTIP_PROVIDER = new MyAttachmentTooltipProvider();
  private static final CommentRenderingHelper COMMENT_RENDERING_HELPER = new CommentRenderingHelper();
  private static final CanvasRenderer<TreeModelBridge<LoadedCommentKey>> COMMENT_RENDERER = new CommentTreeRenderer();
  private static final String BUG_FLAGS_SETTING = "bugFlags";

  private DescriptionController myDescriptionController;

  private final TextDecoratorRegistry myDecorators;

  private BugzillaView2(Configuration config, MetaInfo metaInfo, TextDecoratorRegistry decorators) {
    super(config, metaInfo);
    myDecorators = decorators;
  }

  protected void addLeftSideFields(ItemTableBuilder fields) {
    // todo dummy
    getMetaInfo().addLeftFields(fields);
  }

  protected JComponent createSummaryPanel() {
    return createSummaryPanel(BugzillaKeys.summary);
  }

  protected JComponent createKeyPanel() {
    LargeTextFormlet formlet = LargeTextFormlet.headerWithInt(BugzillaKeys.id, new Convertor<Integer, String>() {
      public String convert(Integer value) {
        return value == null ? "" : "#" + value;
      }
    }, null);
    formlet.adjustFont(1.3F, Font.BOLD, true);
    formlet.setLineWrap(false);
    addHighlightable(formlet);
    return formlet.getComponent();
  }

  protected void addRightSideFormlets(WidthDrivenColumn column, Configuration settings) {
    addBugFlags(column, settings);
    addAttachments(column, settings);

//    addFormlet(column, "Links", new LinksFormlet(myStructure, settings.getOrCreateSubset("links")), 3);

    addDescription(column, settings);
    column.addComponent(createRightCustomFieldFormlets(BugzillaKeys.customFields, settings));
    addComments(column, settings);
  }

  private void addAttachments(WidthDrivenColumn column, Configuration settings) {
    Configuration config = settings.getOrCreateSubset("attachments");
    Comparator<Comparable<AttachmentInfo>> comparator = Containers.comparablesComparator();
    ModelKey<? extends Collection<AttachmentInfo>> key = BugzillaKeys.attachments;
    AttachmentsFormlet<AttachmentInfo> formlet = new AttachmentsFormlet<AttachmentInfo>(config, key);

    formlet.setOrder(comparator);
    formlet.addProperties(ORDER, AttachmentInfo.DESCRIPTION, FILE_NAME, DATE, USER, MIME_TYPE, SIZE);
    formlet.setLabelProperty(AttachmentInfo.DESCRIPTION);
    formlet.setTooltipProvider(ATTACHMENT_TOOLTIP_PROVIDER);
    formlet.addAction(new OpenAttachmentInBrowserAction(), true, false);
    addFormlet(column, "Attachments", formlet, 1);
    addHighlightable(formlet);
  }

  private void addBugFlags(WidthDrivenColumn column, Configuration settings) {
    Configuration config = settings.getOrCreateSubset(BUG_FLAGS_SETTING);
    BugFlagsFormlet formlet = new BugFlagsFormlet(config);
    addFormlet(column, "Flags", formlet, 0);
    addHighlightable(formlet.getHighlightable());
  }

  private void addComments(WidthDrivenColumn column, Configuration settings) {
    CommentsFormlet<LoadedCommentKey> formlet =
      new CommentsFormlet<LoadedCommentKey>(BugzillaKeys.comments, settings.getOrCreateSubset("comments"), LoadedCommentKey.DATA_ROLE, LoadedCommentKey.<LoadedCommentKey>keyComparator(),
        LoadedCommentKey.GET_COMMENTS, COMMENT_RENDERING_HELPER, myDecorators);

    CommentsController<LoadedCommentKey> controller = formlet.getController();
    controller
      .addMenuAction(new OpenCommentInBrowserAction(BugzillaKeys.id), true)
      .addMenuAction(new ChangeCommentPrivacyAction(false), false)
      .setTreeStructure(new CommentsTreeStructure(), COMMENT_RENDERER);
    addHighlightable(formlet);
    addFormlet(column, BugzillaUtil.getDisplayableFieldName("Comments"), formlet, 5);
  }

  private void addDescription(WidthDrivenColumn column, Configuration settings) {
    final JEditorPane textArea = new JEditorPane();
    textArea.setEditorKit(LinksEditorKit.create(Context.require(TextDecoratorRegistry.ROLE), false));

    final List<ToolbarEntry> actions = descriptionActions(textArea);

    myDescriptionController = new DescriptionController();
    UIController.CONTROLLER.putClientValue(textArea, myDescriptionController);
    
    final LinkTextFormlet formlet =
      new LinkTextFormlet(textArea, myDescriptionController, settings.getOrCreateSubset("description")) {
        @Nullable
        public List<? extends ToolbarEntry> getActions() {
          return isCollapsed() ? null : actions;
        }
      };
    addFormlet(column, BugzillaUtil.getDisplayableFieldName("Description"), formlet, 4);
    addHighlightable(formlet);
  }

  private static List<ToolbarEntry> descriptionActions(JTextComponent textArea) {
    Map<String, PresentationMapping<?>> editDescriptionMapping = Collections15.hashMap();
    editDescriptionMapping.putAll(PresentationMapping.NONAME);
    editDescriptionMapping.putAll(PresentationMapping.VISIBLE_ONLY_IF_ENABLED);

    Map<String, PresentationMapping<?>> changePrivacyMapping = Collections15.hashMap();
    changePrivacyMapping.putAll(PresentationMapping.NONAME);
    changePrivacyMapping.putAll(PresentationMapping.VISIBLE_ONLY_IF_ENABLED);
//    changePrivacyMapping.put(Action.SMALL_ICON, PresentationMapping.<Icon>constant(Icons.PRIVATE));

    List<ToolbarEntry> actions = Arrays.<ToolbarEntry>asList(
      new ActionToolbarEntry(EditCommentAction.EDIT_DESCRIPTION, textArea, editDescriptionMapping),
      new ActionToolbarEntry(new ChangeCommentPrivacyAction(true), textArea, changePrivacyMapping));
    return actions;
  }


  public static void addFactory(ElementViewer.CompositeFactory<ItemUiModel> builder, final MetaInfo metaInfo, final TextDecoratorRegistry decorators) {
    builder.addViewer(L.listItem("New view"), new Convertor<Configuration, ElementViewer<ItemUiModel>>() {
      public ElementViewer<ItemUiModel> convert(Configuration config) {
        BugzillaView2 view = new BugzillaView2(config, metaInfo, decorators);
        return new ElementViewerImpl(view, BasicScalarModel.createConstant(BugzillaFormUtils.createViewerToolbar()));
      }
    });
  }


  private static class MyAttachmentTooltipProvider extends AttachmentTooltipProvider<AttachmentInfo> {
    public void addTooltipText(StringBuilder tooltip, AttachmentInfo item, DownloadedFile dfile) {
      append(tooltip, item, dfile, AttachmentInfo.DESCRIPTION, null, null);
      append(tooltip, item, dfile, FILE_NAME, "File: ", null);
      appendSize(tooltip, item, dfile);
      append(tooltip, item, dfile, USER, "Attached By: ", null);
      append(tooltip, item, dfile, DATE, null, null);
    }
  }


  private static class OpenAttachmentInBrowserAction extends SimpleAction {
    public OpenAttachmentInBrowserAction() {
      super("Open in Browser", Icons.ACTION_OPEN_IN_BROWSER);
      watchRole(AttachmentsEnv.ATTACHMENT);
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      Attachment attachment = context.getSourceObject(AttachmentsEnv.ATTACHMENT);
      boolean enabled = false;
      if (attachment instanceof AttachmentInfo) {
        if (attachment.getUrl() != null) {
          Integer id = ((AttachmentInfo) attachment).getId();
          if (id != null && id > 0) {
            enabled = true;
          }
        }
      }
      context.setEnabled(enabled);
    }

    protected void doPerform(ActionContext context) throws CantPerformException {
      Attachment attachment = context.getSourceObject(AttachmentsEnv.ATTACHMENT);
      if (!(attachment instanceof AttachmentInfo))
        return;
      Integer id = ((AttachmentInfo) attachment).getId();
      if (id == null || id <= 0)
        return;
      String attachmentUrl = attachment.getUrl();
      if (attachmentUrl == null)
        return;
      String baseUrl = null;
      try {
        baseUrl = BugzillaIntegration.normalizeURL(attachmentUrl);
      } catch (MalformedURLException e) {
        Log.warn("cannot open attachment", e);
        return;
      }
      String url = BugzillaIntegration.getShowAttachmentURL(baseUrl, id);
      ExternalBrowser browser = new ExternalBrowser();
      browser.setUrl(url, false);
      browser.setExceptionHandler(new Procedure<IOException>() {
        public void invoke(IOException ioException) {
        }
      });
      browser.openBrowser();
    }
  }


  private static class CommentRenderingHelper
    implements com.almworks.api.application.viewer.CommentRenderingHelper<LoadedCommentKey>
  {
    public Color getForeground(LoadedCommentKey comment) {
      return isPrivate(comment) ? PRIVATE_COMMENT_FOREGROUND : null;
    }

    private static boolean isPrivate(LoadedCommentKey comment) {
      Boolean privacy = comment.isPrivate();
      boolean b = privacy != null && privacy;
      return b;
    }

    public static BigDecimal getWorkTime(LoadedCommentKey comment) {
      return comment.getWorkTime();
    }

    @Override
    public String getHeaderPrefix(LoadedCommentKey comment) {
      int index = comment.getIndex();
      return index > 0 ? String.valueOf(comment.getIndex()) + '.' : null;
    }

    @Override
    public String getHeaderSuffix(LoadedCommentKey comment) {
      boolean privateComment = isPrivate(comment);
      BigDecimal workTime = getWorkTime(comment);
      if (privateComment) {
        if (workTime != null) {
          return "(private, hours worked: " + workTime.toPlainString() + ")";
        } else {
          return "(private)";
        }
      } else {
        if (workTime != null) {
          return "(hours worked: " + workTime.toPlainString() + ")";
        } else {
          return null;
        }
      }
    }
  }


  private class DescriptionController extends BaseTextController<List<LoadedCommentKey>> {
    public DescriptionController() {
      super(BugzillaKeys.comments, true);
    }

    protected List<LoadedCommentKey> getEmptyStringValue() {
      return null;
    }

    protected List<LoadedCommentKey> toValue(String text) {
      assert false;
      return null;
    }

    protected boolean isEditable() {
      return false;
    }

    protected String toText(List<LoadedCommentKey> value) {
      assert false;
      return "?.?";
    }

    protected void updateComponent(ModelMap model, JTextComponent component) {
      String text = "";
      Color color = null;
      if (myKey.hasValue(model)) {
        Collection<LoadedCommentKey> value = myKey.getValue(model);
        LoadedCommentKey description = LoadedCommentKey.findDescription(value);
        DataProvider.DATA_PROVIDER.removeAllProviders(component);
        if (description != null) {
          ConstProvider.addRoleValue(component, LoadedCommentKey.DATA_ROLE, description);
          text = Util.NN(description.getText());
          Boolean privacy = description.isPrivate();
          color = privacy != null && privacy ? PRIVATE_COMMENT_FOREGROUND : null;
        }
      }
      setComponentColor(component, color);
      setComponentText(component, text);

      notifyUpdated(text, component);
    }
  }


  private static class CommentTreeRenderer implements CanvasRenderer<TreeModelBridge<LoadedCommentKey>> {


    public void renderStateOn(CellState state, Canvas canvas, TreeModelBridge<LoadedCommentKey> comment) {
      if (comment == null)
        return;
      LoadedCommentKey c = comment.getUserObject();
      if (c == null)
        return;

//      if (index == 0)
//        canvas.setFontStyle(Font.BOLD);
      Boolean privacy = c.isPrivate();
      if (privacy != null && privacy)
        canvas.setForeground(PRIVATE_COMMENT_FOREGROUND);

      if (c.getCommentPlace().isFinal()) {
        canvas.appendText(c.getIndex() + ". ");
      }
      canvas.appendText(c.getWhoText());
    }
  }
}