package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.api.application.viewer.textdecorator.TextDecorationParser;
import com.almworks.api.download.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.DialogManager;
import com.almworks.api.search.TextSearch;
import com.almworks.bugzilla.provider.attachments.*;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.engine.gui.attachments.AttachmentsController;
import com.almworks.engine.gui.attachments.AttachmentsControllerUtil;
import com.almworks.items.api.*;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.ui.actions.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The reference parser responsible for decorating Bugzilla bug references
 * in comments and descriptions.
 */
public class BugReferenceParser implements TextDecorationParser {
  /** Regexp for "bug 42", "bug #42". */
  private static final Pattern BUG_REF = Pattern.compile("bug\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

  /** Regexp for "bugs 17, 42, 31". */
  private static final Pattern BUGS_REF = Pattern.compile("bugs\\s+(\\d+(?:,\\s*\\d+)+)", Pattern.CASE_INSENSITIVE);

  /** Regexp for "bug 123, comment 456" */
  private static final Pattern EXT_COMMENT_REF = Pattern.compile("(bug\\s+(\\d+),\\s*)?comment\\s+#?(\\d+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern ATTACHMENT_REF = Pattern.compile("attachment\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ATTACHMENT_REF2 = Pattern.compile("Created an attachment \\(id=(\\d+)\\)", Pattern.CASE_INSENSITIVE);

  public void decorate(Context context) {
    collectMatches(context, BUG_REF);
    collectMatches(context, BUGS_REF);
    collectCommentRefs(context, EXT_COMMENT_REF);
    collectAttachmentRefs(context, ATTACHMENT_REF);
    collectAttachmentRefs(context, ATTACHMENT_REF2);
  }

  private void collectAttachmentRefs(Context context, Pattern pattern) {
    Matcher m = pattern.matcher(context.getText());
    while (m.find()) {
      String strId = m.group(1);
      int id;
      try {
        id = Integer.parseInt(strId);
        if (id < 0 ) continue;
      } catch (NumberFormatException e) {
        continue;
      }
      context.addLink(m).setDefaultAction(new ViewAttachment(id));
    }
  }

  private void collectCommentRefs(Context context, Pattern pattern) {
    Matcher m = pattern.matcher(context.getText());
    while (m.find()) {
      int bugId;
      String strBugId = m.group(2);
      try {
        if (strBugId != null) {
          bugId = Integer.parseInt(strBugId);
          if (bugId < 0) continue;
        } else
          bugId = -1;
      } catch (NumberFormatException e) {
        continue;
      }
      String strId = m.group(3);
      int commentId;
      try {
        commentId = Integer.parseInt(strId);
      } catch (NumberFormatException e) {
        continue;
      }
      if (commentId <= 0) continue;
      LinkArea link = context.addLink(m);
      link.setDefaultAction(ShowCommentAction.view(bugId, commentId));
      link.addActions(ShowCommentAction.copyUrl(bugId, commentId));
    }
  }

  public static SearchResult performTextSearch(String searchString, ActionContext context) throws CantPerformException {
    TextSearch textSearch = context.getSourceObject(TextSearch.ROLE);
    ItemWrapper item = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    Connection connection = item.getConnection();
    if (connection == null) return SearchResult.EMPTY;
    TabKey tabKey = new SearchTabKey(connection, searchString);
    return textSearch.search(searchString, connection, tabKey);
  }

  private void collectMatches(Context context, Pattern pattern) {
    final Matcher m = pattern.matcher(context.getText());
    while(m.find()) {
      final LinkArea area = context.addLink(m);
      area.setDefaultAction(new BugSearchAction(m.group(1)));
    }
  }

  /**
   * An action that runs a text search over a connection that
   * it obtains from an ItemWrapper, which in turn is
   * obtained from ActionContext.
   * Utilizes TextSearch's ability to search by bug numbers.
   */
  private static class BugSearchAction extends SimpleAction {
    private final String mySearchString;

    public BugSearchAction(String searchString) {
      super("View bug " + searchString);
      mySearchString = searchString;
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {}

    protected void doPerform(ActionContext context) throws CantPerformException {
      String searchString = mySearchString;
      performTextSearch(searchString, context);
    }
  }

  /**
   * Tab key for a text search: tab keys are equal if their respective
   * connections and search strings are equal.
   */
  private static class SearchTabKey implements TabKey {
    private final Connection myConnection;
    private final String mySearchString;

    public SearchTabKey(@NotNull Connection connection, @NotNull String searchString) {
      this.myConnection = connection;
      this.mySearchString = searchString;
    }

    public boolean isReplaceTab(TabKey tabKey) {
      return equals(tabKey);
    }

    @Override
    public int hashCode() {
      return (31 + myConnection.hashCode()) * 31 + mySearchString.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if(obj == null) {
        return false;
      }

      if(obj.getClass() != SearchTabKey.class) {
        return false;
      }

      final SearchTabKey that = (SearchTabKey) obj;
      return that.myConnection.equals(myConnection) 
        && that.mySearchString.equals(mySearchString);
    }
  }

  private class ViewAttachment extends SimpleAction {
    private final int myId;

    public ViewAttachment(int id) {
      super("View Attachment #" + id);
      myId = id;
    }

    protected void customUpdate(UpdateContext context) throws CantPerformException {
      BugzillaConnection.getInstance(context);
    }

    protected void doPerform(ActionContext context) throws CantPerformException {
      ThreadGate.LONG.execute(SearchAndShowAttachment.create(myId, context));
    }
  }

  private static class SearchAndShowAttachment implements Runnable {
    private final BugzillaConnection myConnection;
    private final ItemKeyCache myCache;
    private final AttachmentsController myController;
    private final Component myComponent;
    private final DownloadManager myDownloader;
    private final DialogManager myDM;
    private final int myId;

    public SearchAndShowAttachment(int id, BugzillaConnection connection, ItemKeyCache cache,
      AttachmentsController controller, Component component, DownloadManager downloader, DialogManager dm)
    {
      myId = id;
      myConnection = connection;
      myCache = cache;
      myController = controller;
      myComponent = component;
      myDownloader = downloader;
      myDM = dm;
    }

    public static Runnable create(int id, ActionContext context) throws CantPerformException {
      AttachmentsController controller = context.getSourceObject(AttachmentsController.ROLE);
      Component component = context.getComponent();
      ItemKeyCache cache = context.getSourceObject(NameResolver.ROLE).getCache();
      BugzillaConnection connection = BugzillaConnection.getInstance(context);
      DownloadManager downloader = context.getSourceObject(DownloadManager.ROLE);
      DialogManager dm = context.getSourceObject(DialogManager.ROLE);
      return new SearchAndShowAttachment(id, connection, cache, controller, component, downloader, dm);
    }

    public void run() {
      final AttachmentInfo attachment = findAttachment();
      if (attachment != null && attachment.isLocal()) {
        showAttachment(attachment);
        return;
      }
      BugzillaContextImpl context = myConnection.getContext();
      String attachmentUrl =
        attachment != null ? attachment.getUrl() : FinalAttachment.getAttachmentUrl(context, myId);
      if (attachmentUrl == null) return;
      DownloadedFile dFile = myDownloader.getDownloadStatus(attachmentUrl);
      boolean started;
      if (AttachmentsControllerUtil.isDownloadNeeded(dFile) || !AttachmentsControllerUtil.isGoodFile(dFile.getFile())) {
        DownloadRequest request;
        if (attachment != null) request = attachment.createDownloadRequest();
        else request = FinalAttachment.createDownloadRequest(context.getDownloadOwner(), myId, null, -1);
        myDownloader.initiateDownload(attachmentUrl, request, true, false);
        started = true;
      } else started = false;
      String title;
      String description;
      if (attachment != null) {
        title = AttachmentsControllerUtil.getTitle(attachment);
        description = AttachmentsControllerUtil.getDescription(attachment);
      } else {
        title = "Attachment #" + myId;
        description = "";
      }
      AttachmentsControllerUtil.waitDownloadAndView(myDM, myDownloader, started, attachmentUrl, title, description,
        myController.getViewConfig(), myComponent);
    }

    private AttachmentInfo findAttachment() {
      final BugzillaContextImpl context = myConnection.getContext();
      final AttachmentsLink attachmentsLink = context.getMetadata().attachmentsLink;
      return context.getActor(Database.ROLE).readForeground(new ReadTransaction<AttachmentInfo>() {
        @Override
        public AttachmentInfo transaction(DBReader reader) throws DBOperationCancelledException {
          final long attItem = attachmentsLink.findAttachment(reader, myConnection.getConnectionItem(), myId);
          if(attItem <= 0) {
            return null; // Bug with the attachment has to be downloaded first
          } else {
            return AttachmentsModelKey.createAttachmentInfo(
              context, myCache, attItem, SyncUtils.readTrunk(reader, attItem));
          }
        }
      }).waitForCompletion();
    }

    private void showAttachment(final AttachmentInfo attachment) {
      ThreadGate.AWT.execute(new Runnable() {
        public void run() {
          myController.showAttachment(attachment, myComponent);
        }
      });
    }
  }
}
