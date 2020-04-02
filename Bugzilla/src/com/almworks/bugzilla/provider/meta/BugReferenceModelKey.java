package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.util.SingleValueModelKey;
import com.almworks.api.explorer.gui.SimpleModelKey;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.collections.Comparing;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.*;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.text.PlainDocument;
import java.util.Map;

public class BugReferenceModelKey extends AttributeModelKey<String, Long> implements ModelKeyWithOptionalBehaviors {
  private final TypedKey<PlainDocument> myModelKey;
  @NotNull
  private final CanvasRenderer<PropertyMap> myRenderer;
  private final Map<? extends TypedKey, ?> myOptionalBehaviors;

  public BugReferenceModelKey(DBAttribute<Long> duplicateAttr, String displayName, Map optionalBehaviors) {
    super(duplicateAttr, displayName);
    myOptionalBehaviors = optionalBehaviors;
    myModelKey = TypedKey.create(getName() + "#model");
    myRenderer = SingleValueModelKey.createToStringRenderer(getValueKey());
  }

  public BugReferenceModelKey(DBAttribute<Long> duplicateAttr, String displayName, Map optionalBehaviors, String keyId) {
    super(duplicateAttr, displayName, keyId);
    myOptionalBehaviors = optionalBehaviors;
    myModelKey = TypedKey.create(getName() + "#model");
    myRenderer = SingleValueModelKey.createToStringRenderer(getValueKey());
  }

  @Override
  public <T> T getOptionalBehavior(TypedKey<T> key) {
    return key == null || myOptionalBehaviors == null ? null : key.getFrom(myOptionalBehaviors);
  }

  @Override
  public String getValue(ModelMap model) {
    return DocumentUtil.getDocumentText(model.get(myModelKey));
  }

  @Override
  public <SM> SM getModel(Lifespan lifespan, ModelMap model, Class<SM> aClass) {
    if (!aClass.isAssignableFrom(PlainDocument.class))
      throw new Failure(aClass.getName());
    return (SM) model.get(myModelKey);
  }

  @Override
  public boolean hasValue(ModelMap model) {
    return model.get(myModelKey) != null;
  }

  @Override
  public boolean isEqualValue(ModelMap models, PropertyMap values) {
    assert hasValue(models);
    String valuesValue = getValue(values);
    String modelValue = getValue(models);
    return valuesValue == null ? modelValue.trim().length() == 0 : modelValue.equals(valuesValue);
  }

  @Override
  public boolean isEqualValue(PropertyMap values1, PropertyMap values2) {
    return Util.equals(getValue(values1), getValue(values2));
  }

  @Override
  public void copyValue(final ModelMap to, PropertyMap from) {
    setModelValue(to, getValue(from));
  }

  private void setModelValue(final ModelMap model, String value) {
    PlainDocument document;
    document = model.get(myModelKey);
    String string = value != null ? value : "";
    if (document == null) {
      document = new PlainDocument();
      DocumentUtil.setDocumentText(document, string);
      DocumentUtil.addListener(Lifespan.FOREVER, document, SimpleModelKey.createNotifyOnChange(model, this));
      model.put(myModelKey, document);
      model.registerKey(getName(), this);
    } else {
      DocumentUtil.setDocumentText(document, string);
    }
  }

  public void addChanges(UserChanges changes) {
    String newValue = changes.getNewValue(this);
    try {
      long item = CommonMetadata.bugResolver.resolve(newValue, changes);
      ItemVersionCreator creator = changes.getCreator();
      if (item > 0) creator.setValue(getAttribute(), item);
      else creator.setValue(getAttribute(), (Long)null);
    } catch (BadItemKeyException e) {
      changes.invalidValue(this, newValue);
    }
  }

  @Override
  public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
    Long bug = itemVersion.getValue(getAttribute());
    if (bug == null || bug <= 0) {
      setValue(values, null);
    } else {
      Integer id = itemVersion.forItem(bug).getValue(Bug.attrBugID);
      assert id != null : bug;
      setValue(values, id.toString());
    }
  }

  @NotNull
  public CanvasRenderer<PropertyMap> getRenderer() {
    return myRenderer;
  }

  public void takeSnapshot(PropertyMap to, ModelMap from) {
    String text = DocumentUtil.getDocumentText(from.get(myModelKey));
    setValue(to, text.trim().length() == 0 ? null : text);
  }

  public int compare(String o1, String o2) {
    return Comparing.compare(o1, o2);
  }

  public ModelMergePolicy getMergePolicy() {
    return ModelMergePolicy.MANUAL;
  }

  public <T>ModelOperation<T> getOperation(TypedKey<T> key) {
    if (ModelOperation.SET_STRING_VALUE.equals(key)) {
      return (ModelOperation) new ModelOperation<String>() {
        public void perform(ItemUiModel model, String argument) {
          setModelValue(model.getModelMap(), argument);
        }

        public String getArgumentProblem(String argument) {
          if (argument == null)
            return "";
          argument = argument.trim();
          int length = argument.length();
          if (length == 0)
            return "";
          for (int i = 0; i < length; i++)
            if (!Character.isDigit(argument.charAt(i)))
              return "";
          return null;
        }
      };
    } else {
      return super.getOperation(key);
    }
  }
}
