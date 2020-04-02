package com.almworks.engine.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelKey;
import com.almworks.api.application.util.ModelKeyUtils;
import com.almworks.spellcheck.SpellCheckManager;
import com.almworks.util.collections.Convertor;
import com.almworks.util.collections.Convertors;
import com.almworks.util.text.TextUtil;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.text.html.HTMLEditorKit;

/**
 * @author dyoma
 */
public class TextController<T> extends BaseTextController<T> {
  @NotNull
  private final Convertor<T, String> myToTextConvertor;

  @Nullable
  private final Convertor<String, T> myFromTextConvertor;

  @Nullable
  private final T myEmptyValue;

  public TextController(ModelKey<T> key, @NotNull Convertor<T, String> toTextConvertor,
    @Nullable Convertor<String, T> fromText, @Nullable T emptyValue, boolean directSet)
  {
    super(key, directSet);
    myToTextConvertor = toTextConvertor;
    myFromTextConvertor = fromText;
    myEmptyValue = emptyValue;
  }

  protected T getEmptyStringValue() {
    return myEmptyValue;
  }

  protected T toValue(String text) {
    Convertor<String, T> textConvertor = myFromTextConvertor;
    assert textConvertor != null;
    return textConvertor == null ? null : textConvertor.convert(text);
  }

  protected boolean isEditable() {
    return myFromTextConvertor != null;
  }

  protected String toText(T value) {
    return myToTextConvertor.convert(value);
  }

  public static void installItem(JTextComponent component, ModelKey<ItemKey> key) {
    CONTROLLER.putClientValue(component, new TextController<ItemKey>(key, ItemKey.DISPLAY_NAME, null, null, false));
  }

  public static void installInt(JTextComponent component, ModelKey<Integer> key) {
    CONTROLLER.putClientValue(component, intEditor(key));
  }

  public static TextController<String> installTextEditor(JTextComponent component, ModelKey<String> key, boolean directSet) {
    return installController(component, textEditor(key, directSet));
  }

  public static TextController<String> installTextSpellCheckedEditor(JTextPane component,
    ModelKey<String> key, Lifespan life)
  {
    SpellCheckManager.attach(life, component);
    return installController(component, textEditor(key, false));
  }

  public static TextController<String> installTextViewer(JTextComponent component, ModelKey<String> key, boolean directSet) {
    return installController(component, textViewer(key, directSet));
  }

  private static <T> TextController<T> installController(JComponent component, TextController<T> controller) {
    CONTROLLER.putClientValue(component, controller);
    return controller;
  }

  public static TextController<String> installHtml(JEditorPane component, ModelKey<String> key) {
    HTMLEditorKit kit = new HTMLEditorKit();
    kit.setStyleSheet(null);
    component.setEditorKit(kit);
    component.setEditable(false);
    return installController(component, new TextController<String>(key, Convertors.<String>identity(), null, null, false) {
      protected void setComponentText(JTextComponent textComponent, String text) {
        textComponent.setText(TextUtil.preprocessHtml(text));
      }
    });
  }

  public static TextController<String> textEditor(ModelKey<String> key, boolean directSet) {
    return new TextController<String>(key, Convertors.<String>identity(), Convertors.<String>identity(), null, directSet);
  }

  public static TextController<String> textViewer(ModelKey<String> key, boolean directSet) {
    return new TextController<String>(key, Convertors.<String>identity(), null, null, directSet);
  }

  public static TextController<Object> anyTextViewer(ModelKey<?> key, boolean directSet) {
    return new TextController<Object>((ModelKey<Object>) key, ModelKeyUtils.ANY_TO_STRING, null, null, directSet);
  }

  public static TextController<Integer> intEditor(ModelKey<Integer> key) {
    return new TextController<Integer>(key, Convertors.<Integer>getToString(), new Convertor<String, Integer>() {
      public Integer convert(String s) {
        try {
          return Integer.parseInt(s);
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }, null, false);
  }
}
