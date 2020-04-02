package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.*;
import com.almworks.api.explorer.gui.ResolverItemFactory;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.TODO;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.text.*;

/**
 * @author : Dyoma
 */
public class DescriptionModelKey extends AttributeModelKey<String, String> {
  // todo: get rid of this
  private final static DBAttribute<String> DUMMY = Bug.BUG_NS.string("description", "Description", false);

  private final TypedKey<Document> myModelKey;
  private final TypedKey<Boolean> myPrivacyKey;

  private final ResolverItemFactory myResolver;

  public DescriptionModelKey(ResolverItemFactory resolver) {
    super(DUMMY, BugzillaUtil.getDisplayableFieldName("Description"));
    myResolver = resolver;
    myModelKey = TypedKey.create(getName() + "#model");
    myPrivacyKey = TypedKey.create(getName() + "#private");
  }

  public String getValue(ModelMap model) {
    throw TODO.shouldNotHappen(getDisplayableName());
  }

  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    Document document = model.get(myModelKey);
    if (aClass.isAssignableFrom(PlainDocument.class)) {
      if (document == null) {
        document = new PlainDocument();
        model.put(myModelKey, document);
      }
    } else if (aClass.isAssignableFrom(DefaultStyledDocument.class)) {
      assert (document == null);
      document = new DefaultStyledDocument();
      model.put(myModelKey, document);
    } else {
      assert false : aClass;
    }
    return (SM) document;
  }

  public boolean hasValue(ModelMap model) {
    return true;
  }

  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    return !hasDescription(models);
  }

  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return true;
  }

  public void copyValue(ModelMap to, PropertyMap from) {
  }

  public void addChanges(UserChanges changes) {
    final String newValue = changes.getNewValue(this);
    if(newValue == null) {
      return;
    }

    final long item = myResolver.createItem(newValue, changes);
    final Boolean privacy = changes.getNewValue(myPrivacyKey);
    if(privacy != null) {
      changes.getCreator().changeItem(item).setValue(CommentsLink.attrPrivate, privacy);
    }
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {}

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    if(hasDescription(from)) {
      Document document = from.get(myModelKey);
      setValue(to, DocumentUtil.getDocumentText(document));
      to.put(myPrivacyKey, from.get(myPrivacyKey));
    }
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    throw TODO.shouldNotHappen(getDisplayableName());
  }

  public int compare(String o1, String o2) {
    return 0;
  }

  public ModelMergePolicy getMergePolicy() {
    return ModelMergePolicy.IGNORE;
  }

  private boolean hasDescription(ModelMap models) {
    final Document document = models.get(myModelKey);
    return document != null && DocumentUtil.getDocumentText(document).trim().length() > 0;
  }

  public static DescriptionModelKey find(ModelMap models) {
    return BugzillaKeys.comments.getDescriptionKey();
  }

  public void setDescriptionPrivate(ModelMap model, boolean privacy) {
    model.put(myPrivacyKey, privacy);
  }
}
