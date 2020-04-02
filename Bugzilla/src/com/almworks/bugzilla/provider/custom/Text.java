package com.almworks.bugzilla.provider.custom;

import com.almworks.api.application.*;
import com.almworks.api.application.field.AdaptiveComparator;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.order.StringAttributeOrder;
import com.almworks.api.application.qb.AttributeConstraintDescriptor;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.util.*;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.explorer.util.ConnectContext;
import com.almworks.bugzilla.provider.BugzillaCustomFields;
import com.almworks.engine.gui.LeftFieldsBuilder;
import com.almworks.explorer.qbuilder.filter.TextAttribute;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.AScrollPane;
import com.almworks.util.components.renderer.FontStyle;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.components.renderer.table.*;
import com.almworks.util.models.TableColumnBuilder;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.threads.CanBlock;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.text.PlainDocument;

/**
 * @author Alex
 */
public abstract class Text extends BugzillaCustomField<String, String> {
  public Text(DBAttribute<String> attribute, String id, String displayName, Boolean availableOnSubmit, int order,
    BugzillaCustomFields fields, ModelKey<String> modelKey)
  {
    super(attribute, id, displayName, availableOnSubmit, fields, order, modelKey);
  }

  public boolean isTextSearchEnabled() {
    return true;
  }

  protected void buildColumn(TableColumnBuilder<LoadedItem, String> builder) {
    builder.setValueCanvasRenderer(Renderers.defaultCanvasRenderer());
    builder.setValueComparator(AdaptiveComparator.instance());
    builder.setConvertor(new Convertor<LoadedItem, String>() {
      public String convert(LoadedItem value) {
        return getModelKey().getValue(value.getValues());
      }
    });
  }

  protected void buildKey(BaseKeyBuilder<String> builder) {
    builder.setValueRenderer(Renderers.defaultCanvasRenderer());
    builder.setIO(new BaseModelKey.DataIO<String>() {
      public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<String> modelKey) {
      }

      public void addChanges(UserChanges changes, ModelKey<String> modelKey) {
        String value = changes.getNewValue(modelKey);
        if (value != null) {
          value = value.trim();
          if (value.length() == 0) value = null;
        }
        changes.getCreator().setValue(getAttribute(), value);
      }

      public <SM> SM getModel(Lifespan life, final ModelMap model, final ModelKey<String> modelKey,
        Class<SM> aClass)
      {
        if (!aClass.isAssignableFrom(PlainDocument.class)) {
          assert false : aClass;
          return null;
        }
        final PlainDocument document = new PlainDocument();
        String value = modelKey.getValue(model);
        DocumentUtil.setDocumentText(document, value == null ? "" : Util.NN(value));
        DocumentUtil.addChangeListener(life, document, new ChangeListener() {
          public void onChange() {
            String text = DocumentUtil.getDocumentText(document);
            PropertyMap props = new PropertyMap();
            modelKey.setValue(props, text);
            modelKey.copyValue(model, props);
          }
        });
        return (SM) document;
      }
    });
    builder.setAccessor(new StringAccessor(Text.this.getId()));
  }

  @CanBlock
  public String loadValue(ItemVersion version, LoadedItemServices itemServices) {
    return version.getValue(getAttribute());
  }

  protected ConstraintDescriptor createDescriptor() {
    return AttributeConstraintDescriptor.constant(TextAttribute.INSTANCE, getDisplayName(), getAttribute());
  }

  public Order createOrder() {
    return new StringAttributeOrder(getModelKey(), getColumn(), getAttribute());
  }

  /**
   * @author Alex
   */

  public static class TextField extends Text {
    public TextField(DBAttribute<String> revision, String id, String displayName, Boolean availableOnSubmit, int order,
      BugzillaCustomFields fields, ModelKey<String> modelKey)
    {
      super(revision, id, displayName, availableOnSubmit, order, fields, modelKey);
    }

    protected void buildKey(BaseKeyBuilder<String> builder) {
      super.buildKey(builder);
      builder.setExport(BaseModelKey.Export.STRING);
    }

    public JComponent createValueEditor(ConnectContext context) {
      JTextField field = new JTextField();
      DefaultUIController.connectWithKey(field, getModelKey());
      SpellCheckManager.attach(context.getLife(), field);
      return field;
    }

    public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
      return new TextCell(FontStyle.BOLD, new LeftFieldsBuilder.TextTextGetter(getModelKey()));
    }
  }


  /**
   * @author Alex
   */

  public static class TextArea extends Text {
    public TextArea(DBAttribute<String> revision, String id, String displayName, Boolean availableOnSubmit, int order,
      BugzillaCustomFields fields, ModelKey<String> modelKey)
    {
      super(revision, id, displayName, availableOnSubmit, order, fields, modelKey);
    }

    public JComponent createValueEditor(ConnectContext context) {
      JTextArea textArea = new JTextArea();
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      AScrollPane pane = new AScrollPane(textArea);
      pane.setAdaptiveVerticalScroll(true);
      //JScrollPane paned = UIUtil.getScrollPaned(textArea);
      UIUtil.configureBasicScrollPane(pane, 20, 4);
//      UIUtil.set
      DefaultUIController.connectWithKey(textArea, getModelKey());
      SpellCheckManager.attach(context.getLife(), textArea);
      return pane;
    }

    protected void buildKey(BaseKeyBuilder<String> builder) {
      super.buildKey(builder);
      builder.setExport(BaseModelKey.Export.LARGE_STRING);
    }

    @Override
    public boolean isMultilineText() {
      return true;
    }

    public TableRendererCell getViewerCell(ModelMap modelMap, TableRenderer renderer) {
      //todo here
      return null;
    }
  }
}
