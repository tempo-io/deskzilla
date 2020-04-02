package com.almworks.bugzilla.provider.attachments;

import com.almworks.api.application.*;
import com.almworks.api.misc.WorkArea;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.engine.gui.attachments.AttachmentSaveException;
import com.almworks.integers.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.exec.Context;
import com.almworks.util.images.Icons;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

public class AttachmentsModelKey extends MasterSlaveModelKey<AttachmentInfo> {
  public static AttachmentsModelKey INSTANCE = new AttachmentsModelKey();

  private static final Condition<AttachmentInfo> SERVER_ATTACHMENT = new Condition<AttachmentInfo>() {
    public boolean isAccepted(AttachmentInfo attachmentInfo) {
      return attachmentInfo instanceof FinalAttachment;
    }
  };

  private AttachmentsModelKey() {
    super(AttachmentsLink.attrMaster, "attachments", BugzillaUtil.getDisplayableFieldName("Attachments"));
  }

  @Override
  protected List<AttachmentInfo> extractValues(LongList slaves, ItemVersion master, LoadedItemServices services, PropertyMap values)  {
    final BugzillaConnection connection = services.getConnection(BugzillaConnection.class);
    final BugzillaContext context = connection != null ? connection.getContext() : null;
    return createAttachmentInfos(slaves, master, context);
  }

  public static List<AttachmentInfo> createAttachmentInfos(LongList slaves, ItemVersion master, BugzillaContext context) {
    final List<AttachmentInfo> result = Collections15.arrayList();
    final ItemKeyCache resolver = Context.require(NameResolver.class).getCache();
    for(final LongIterator it = slaves.iterator(); it.hasNext();) {
      result.add(createAttachmentInfo(context, resolver, it.next(), master));
    }
    return result;
  }

  /**
   * @param master use for reading values (see {@link ItemVersion#forItem(long)}
   */
  public static AttachmentInfo createAttachmentInfo(BugzillaContext context, ItemKeyCache resolver, long slave, ItemVersion master) {
    final ItemVersion version = master.forItem(slave);
    final Integer id = version.getValue(AttachmentsLink.attrId);
    final String description = version.getNNValue(AttachmentsLink.attrDescription, "");
    final Date date = version.getValue(AttachmentsLink.attrDate);
    final String fileName = version.getValue(AttachmentsLink.attrFileName);
    final String mimeType = version.getValue(AttachmentsLink.attrMimeType);
    final String size = version.getValue(AttachmentsLink.attrSize);
    final String localPlacement = version.getValue(AttachmentsLink.attrLocalPath);
    final Long a = version.getValue(AttachmentsLink.attrSubmitter);
    final ItemKey submitter = a != null ? resolver.getItemKeyOrNull(a, master.getReader(), User.KEY_FACTORY) : null;

    final AttachmentInfo attachment;
    if(id != null) {
      attachment = new FinalAttachment(slave, id, description, submitter, date, mimeType, size, fileName, context);
    } else {
      final WorkArea workArea = Context.require(WorkArea.class);
      final boolean hasLocalFile = (workArea != null && localPlacement != null);
      final File localFile = hasLocalFile ? new File(workArea.getUploadDir(), localPlacement) : null;
      attachment = new LocalAttachment(slave, description, mimeType, size, fileName, localFile);
    }
    return attachment;
  }

  public void addChanges(UserChanges changes) {
    LongArray currentAttachments = changes.getCreator().getSlaves(AttachmentsLink.attrMaster);
    StringBuilder errors = null;
    for(final AttachmentInfo attachment : changes.getNewValue(this)) {
      try {
        long item = attachment.resolveOrCreate(changes);
        currentAttachments.remove(item);
      } catch (AttachmentSaveException e) {
        if (errors == null) {
          errors = new StringBuilder(e.getMessage());
        } else {
          errors.append("; ").append(e.getMessage());
        }
      }
    }
    SyncUtils.deleteAll(changes.getCreator(), currentAttachments);
    if(errors != null) {
      changes.invalidValue(this, errors.toString());
    }
  }

  public ModelMergePolicy getMergePolicy() {
    return new ModelMergePolicy.AbstractPolicy() {
      public boolean autoMerge(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap branch) {
        mergeIntoModel(key, model, base, branch);
        return true;
      }

      public void mergeIntoModel(ModelKey<?> key, ModelMap model, PropertyMap base, PropertyMap newLocal) {
        Collection<AttachmentInfo> newList = getValue(newLocal);
        Change change = change(model);
        List<AttachmentInfo> newValue = change.newValue();
        SERVER_ATTACHMENT.removeAllFrom(newValue);
        newValue.addAll(0, SERVER_ATTACHMENT.select(newList));
        change.done();
      }
    };
  }

  public CanvasRenderer<PropertyMap> createRenderer() {
    return new CanvasRenderer<PropertyMap>() {
      private final Insets myMargin = new Insets(0, 1, 0, 1);

      public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
        final Collection<AttachmentInfo> value = getValue(item);
        if(value != null && !value.isEmpty()) {
          canvas.setIcon(Icons.ATTACHMENT);
          canvas.setIconMargin(myMargin);
          canvas.appendText("" + value.size());
        }
      }
    };
  }

  public ColumnSizePolicy getRendererSizePolicy() {
    return ColumnSizePolicy.FREE;
  }

  public AttachmentInfo createNew(NewAttachment data, ModelMap modelMap) {
    Change change = change(modelMap);
    change.newValue().add(data);
    change.done();
    return data;
  }

  public DataPromotionPolicy getDataPromotionPolicy() {
    return DataPromotionPolicy.ALWAYS;
  }
}
