package com.almworks.api.explorer.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.util.SingleValueModelKey;
import com.almworks.api.engine.Connection;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.collections.Comparing;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;

import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * @author : Dyoma
 */
public abstract class SimpleModelKey <M, V> extends SingleValueModelKey<M, V> {
  protected SimpleModelKey(DBAttribute<V> attribute, String displayableName, boolean isMultilineExport) {
    super(attribute, false, isMultilineExport, displayableName);
  }

  public V getValue(ModelMap model) {
    M m = findSingleValue(model);
    return extractValue(m);
  }

  protected abstract void changeModelValue(M m, V value);

  protected abstract M createModel(ModelMap model);

  protected abstract V extractValue(M m);

  public void copyValue(ModelMap to, PropertyMap from) {
    M m = findSingleValue(to);
    if (m == null) {
      m = createModel(to);
      setupSingleValue(to, m);
      register(to);
    } else if (isEqualValue(to, from))
      return;
    changeModelValue(m, getValue(from));
    to.valueChanged(this);
  }

  public static SimpleModelKey<Document, String> text(DBAttribute<String> attribute, String displayableName, boolean isMultiline) {
    return new TextModelKey(attribute, displayableName, isMultiline);
  }

  public static SimpleModelKey<StyledDocument, String> styledText(DBAttribute<String> attribute, String displayableName, boolean isMultiline) {
    return new StyledTextModelKey(attribute, displayableName, isMultiline);
  }

  public static SimpleModelKey<Document, BigDecimal> decimal(DBAttribute<BigDecimal> attribute, String displayableName) {
    return new DecimalModelKey(attribute, displayableName);
  }

  public static SimpleModelKey<Document, BigDecimal> decimalUnexportable(DBAttribute<BigDecimal> attribute, String displayableName) {
    return new DecimalModelKey(attribute, displayableName) {
      @Override
      public boolean isExportable(Collection<Connection> connections) {
        return false;
      }
    };
  }

  public static DocumentAdapter createNotifyOnChange(final ModelMap model, final ModelKey key) {
    return new DocumentAdapter() {
      protected void documentChanged(DocumentEvent e) {
        model.valueChanged(key);
      }
    };
  }

  private static class TextModelKey extends AbstractTextModelKey<Document> {
    public TextModelKey(DBAttribute<String> attribute, String displayableName, boolean isMultiline) {
      super(attribute, displayableName, isMultiline);
    }

    protected void changeModelValue(Document document, String value) {
      DocumentUtil.setDocumentText(document, Util.NN(value));
    }

    protected Document createModel(final ModelMap model) {
      PlainDocument document = new PlainDocument();
      final TextModelKey key = TextModelKey.this;
      document.addDocumentListener(createNotifyOnChange(model, key));
      return document;
    }
  }

  private static class StyledTextModelKey extends AbstractTextModelKey<StyledDocument> {
    public StyledTextModelKey(DBAttribute<String> attribute, String displayableName, boolean isMultiline) {
      super(attribute, displayableName, isMultiline);
    }

    protected StyledDocument createModel(final ModelMap model) {
      DefaultStyledDocument document = new DefaultStyledDocument();
      final StyledTextModelKey key = StyledTextModelKey.this;
      document.addDocumentListener(createNotifyOnChange(model, key));
      return document;
    }
  }

  private static abstract class AbstractTextModelKey<T extends Document> extends SimpleModelKey<T, String> {
    public AbstractTextModelKey(DBAttribute<String> attribute, String displayableName, boolean isMultilineExport) {
      super(attribute, displayableName, isMultilineExport);
    }

    protected void changeModelValue(T document, String value) {
      DocumentUtil.setDocumentText(document, Util.NN(value));
    }

    @Override
    public void addChanges(UserChanges changes) {
      String userInput = changes.getNewValue(this);
      if (userInput != null) userInput = userInput.trim();
      if (userInput != null && userInput.length() == 0) userInput = null;
      changes.getCreator().setValue(getAttribute(), userInput);
    }

    protected String extractValue(T document) {
      return DocumentUtil.getDocumentText(document);
    }

    public int compare(String o1, String o2) {
      return Comparing.compare(o1, o2);
    }

    public void setValue(PropertyMap values, String value) {
      super.setValue(values, Util.NN(value));
    }

    @Override
    public <O> ModelOperation<O> getOperation(TypedKey<O> key) {
      ModelOperation<O> operation = super.getOperation(key);
      if (operation != null) return operation;
      if (ModelOperation.SET_STRING_VALUE.equals(key)) return (ModelOperation<O>) new SetValueOperation<T, String>(this);
      return null;
    }
  }

  private static class DecimalModelKey extends SimpleModelKey<Document, BigDecimal> {
    private static final BigDecimal ZERO = BigDecimal.valueOf(0);

    public DecimalModelKey(DBAttribute<BigDecimal> attribute, String displayableName) {
      super(attribute, displayableName, false);
    }

    protected void changeModelValue(Document document, BigDecimal value) {
      DocumentUtil.setDocumentText(document, value == null ? "" : value.toString());
    }

    protected Document createModel(final ModelMap model) {
      PlainDocument document = new PlainDocument();
      final DecimalModelKey key = DecimalModelKey.this;
      document.addDocumentListener(createNotifyOnChange(model, key));
      return document;
    }

    protected BigDecimal extractValue(Document document) {
      String string = DocumentUtil.getDocumentText(document);
      try {
        return TextUtil.parseBigDecimal(string);
      } catch (NumberFormatException e) {
        // todo visual validate
        return ZERO;
      }
    }

    public int compare(BigDecimal o1, BigDecimal o2) {
      return Comparing.compare(o1, o2);
    }

    public void setValue(PropertyMap values, BigDecimal value) {
      if (value == null)
        value = ZERO;
      super.setValue(values, value);
    }
  }
}
