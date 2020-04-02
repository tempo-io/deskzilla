package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.UserChanges;
import com.almworks.api.explorer.gui.ResolverItemFactory;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.SyncAttributes;
import org.almworks.util.Collections15;

import java.util.Map;

/**
 * @author dyoma
 */
public class CommentsFactory implements ResolverItemFactory {
  public long createItem(String text, UserChanges changes) {
    ItemVersionCreator creator = changes.getCreator().createItem();
    ItemVersionCreator edittedBug = changes.getCreator();
    return setCommentValues(text, creator, edittedBug.getItem());
  }

  public static long setCommentValues(String text, ItemVersionCreator comment, long bug) {
    Long connection = comment.forItem(bug).getValue(SyncAttributes.CONNECTION);
    SyncUtils.copyValues(comment, setCommentValues(text, bug));
    comment.setValue(SyncAttributes.CONNECTION, connection);
    return comment.getItem();
  }

  public static Map<DBAttribute<?>, Object> setCommentValues(String text, long bug) {
    Map<DBAttribute<?>, Object> comment = Collections15.hashMap();
    comment.put(DBAttribute.TYPE, CommentsLink.typeComment);
    comment.put(CommentsLink.attrMaster, bug);
    comment.put(CommentsLink.attrText, text);
    return comment;
  }
}
