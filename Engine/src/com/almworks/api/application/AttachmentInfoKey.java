package com.almworks.api.application;

import com.almworks.api.engine.Connection;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Terms;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.properties.Role;
import org.almworks.util.Collections15;

import java.util.Collection;

public class AttachmentInfoKey extends SystemKey<Collection<? extends Attachment>>
  implements AutoAddedModelKey<Collection<? extends Attachment>>
{
  public static final Role<AttachmentInfoKey> ROLE = Role.role(AttachmentInfoKey.class);
  public static final StateIcon ATTACHMENT_STATE_ICON = new StateIcon(Icons.ATTACHMENT, 0, Terms.ref_Artifact + " has attachments");

  public AttachmentInfoKey() {
    super("attachedFiles");
  }

  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    Connection connection = itemServices.getConnection();
    Collection<? extends Attachment> attachmentInfos;
    if (connection == null) {
      attachmentInfos = Collections15.emptyCollection();
    } else {
      attachmentInfos = connection.getItemAttachments(itemVersion);
    }
    values.put(getModelKey(), attachmentInfos);
    if (attachmentInfos.size() > 0) {
      StateIconHelper.addStateIcon(values, AttachmentInfoKey.ATTACHMENT_STATE_ICON);
    }
  }
}
