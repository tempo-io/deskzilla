package com.almworks.api.explorer;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.PlainTextCanvas;
import com.almworks.util.components.ReadOnlyTextFields;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifecycle;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import javax.swing.text.JTextComponent;

public class ReadOnlyTextController extends DefaultUIController<JTextComponent> {
  private static final ReadOnlyTextController INSTANCE = new ReadOnlyTextController();

  protected void connectUI(Lifespan lifespan, final ModelMap model, final JTextComponent component, final ModelKey key) {
    final PropertyMap map = new PropertyMap();
    final Lifecycle lifecycle = new Lifecycle();
    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        show(model, component, key, map, lifecycle);
      }
    };
    listener.onChange();
    model.addAWTChangeListener(lifespan, listener);
    lifespan.add(lifecycle.getDisposeDetach());
  }

  protected String decorateText(String text) {
    return text;
  }

  private String getText(ModelMap model, ModelKey key, PlainTextCanvas canvas, PropertyMap map) {
    key.takeSnapshot(map, model);
    canvas.clear();
    key.getRenderer().renderStateOn(CellState.LABEL, canvas, map);
    String text = canvas.getText();
    text = decorateText(text);
    return Util.NN(text);
  }

  private void show(ModelMap model, JTextComponent component, ModelKey key, PropertyMap map, Lifecycle lifecycle) {
    PlainTextCanvas canvas = new PlainTextCanvas();
    String text = getText(model, key, canvas, map);
    showText(lifecycle, component, text);
  }

  private void showText(Lifecycle lifecycle, JTextComponent component, String text) {
    lifecycle.cycle();
    if (!(component instanceof JTextField)) {
      ReadOnlyTextFields.setText(component, text);
    } else {
      lifecycle.lifespan().add(ReadOnlyTextFields.setTextFieldText(component, text));
    }
  }

  public static void install(JTextComponent component) {
    CONTROLLER.putClientValue(component, INSTANCE);
  }

  public static void install(JTextComponent component, final ModelKey key) {
    CONTROLLER.putClientValue(component, new ReadOnlyTextController() {
      protected ModelKey getKey(JTextComponent component, ModelMap model) {
        return key;
      }
    });
  }
}
