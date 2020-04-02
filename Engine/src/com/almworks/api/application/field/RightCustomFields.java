package com.almworks.api.application.field;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.engine.gui.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Procedure;
import com.almworks.util.components.ATextArea;
import com.almworks.util.components.Highlightable;
import com.almworks.util.components.layout.WidthDrivenColumn;
import com.almworks.util.components.layout.WidthDrivenComponent;
import com.almworks.util.config.Configuration;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.DocumentFormAugmentor;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;

/**
 * @author dyoma
 */
public class RightCustomFields implements UIController<WidthDrivenColumn> {
  private final ModelKey<List<ModelKey<?>>> myKey;
  private final Configuration mySettings;
  private final DocumentFormAugmentor myDocumentFormAugmentor;

  private final Procedure<Highlightable> myDetach;
  private final Procedure<Highlightable> myAttach;

  private final Lifecycle myKeyListLife = new Lifecycle();
  private List<ModelKey<?>> myLastKeys;
  private PropertyMap myLastValues;

  private RightCustomFields(ModelKey<List<ModelKey<?>>> key, Configuration settings,
    DocumentFormAugmentor documentFormAugmentor, Procedure<Highlightable> attach, Procedure<Highlightable> detach)
  {
    myKey = key;
    mySettings = settings;
    myDocumentFormAugmentor = documentFormAugmentor;
    myAttach = attach;
    myDetach = detach;
  }

  public static WidthDrivenComponent createComponent(ModelKey<List<ModelKey<?>>> key, Configuration settings,
    DocumentFormAugmentor documentFormAugmentor, Procedure<Highlightable> attaHighlightableProcedure,
    Procedure<Highlightable> detachAllHi)
  {
    WidthDrivenColumn result = new WidthDrivenColumn();
    CONTROLLER.putClientValue(result,
      new RightCustomFields(key, settings, documentFormAugmentor, attaHighlightableProcedure, detachAllHi));
    return result;
  }

  public void connectUI(@NotNull final Lifespan lifespan, @NotNull final ModelMap model,
    @NotNull final WidthDrivenColumn component)
  {
    if (lifespan.isEnded())
      return;
    lifespan.add(myKeyListLife.getAnyCycleDetach());
    myLastKeys = getModelValue(model);
    myLastValues = getValues(myLastKeys, model);
    rebuild(model, component, myLastKeys);
    model.addAWTChangeListener(lifespan, new ChangeListener() {
      public void onChange() {
        if (!lifespan.isEnded()) {
          List<ModelKey<?>> keys = getModelValue(model);
          if (!Util.equals(keys, myLastKeys) || !equalValues(keys, model, myLastValues)) {
            myLastKeys = keys;
            myLastValues = getValues(keys, model);
            rebuild(model, component, myLastKeys);
            Container parent = component.getParent();
            if (parent.isShowing()) {
              parent.invalidate();
              parent.repaint();
            }
          }
        }
      }
    });
  }

  private List<ModelKey<?>> getModelValue(ModelMap model) {
    List<ModelKey<?>> value = myKey.getValue(model);
    if (value == null) value = Collections15.emptyList();
    return value;
  }

  @NotNull
  private static PropertyMap getValues(List<ModelKey<?>> cfKeys, ModelMap model) {
    PropertyMap result = new PropertyMap();
    for (ModelKey<?> cfKey : cfKeys) {
      cfKey.takeSnapshot(result, model);
    }
    return result;
  }

  private static boolean equalValues(List<ModelKey<?>> cfKeys, ModelMap model, PropertyMap values) {
    for (ModelKey<?> cfKey : cfKeys) {
      if (!cfKey.isEqualValue(model, values)) return false;
    }
    return true;
  }

  private void rebuild(ModelMap model, WidthDrivenColumn component, List<ModelKey<?>> keys) {
    Lifespan lifespan = myKeyListLife.lifespan();
    component.removeAllComponents();
    if (keys == null)
      return;
    for (ModelKey<?> key : keys) {
      if (!key.hasValue(model))
        continue;
      ItemField<?, ?> cfAttr = CustomFieldsHelper.getField(key, model);
      if (cfAttr == null)
        continue;
      if (!cfAttr.isMultilineText())
        continue;
      TextController<Object> controller = TextController.anyTextViewer(key, false);
      ATextArea area = new ATextArea();
      final LargeTextFormlet largeTextFormlet =
        new LargeTextFormlet(area, controller, mySettings.getOrCreateSubset(cfAttr.getId()));
      CommonIssueViewer.addFormlet(component, cfAttr.getDisplayName(), largeTextFormlet, -1);
      myAttach.invoke(largeTextFormlet);
      lifespan.add(new Detach() {
        protected void doDetach() throws Exception {
          myDetach.invoke(largeTextFormlet);
        }
      });
      controller.connectUI(lifespan, model, area);
      myDocumentFormAugmentor.setupDescendantsOpaque(component);
      component.setOpaque(false);
    }
  }
}
