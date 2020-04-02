package com.almworks.api.explorer.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.DefaultUIController;
import com.almworks.api.application.viewer.UIController;
import com.almworks.util.advmodel.AComboboxModel;
import com.almworks.util.components.AComboBox;
import com.almworks.util.components.DelegatingComboBoxEditor;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
public class ComboBoxItemEditor extends DelegatingComboBoxEditor<ItemKey> {
  private final TextResolver myResolver;

  private ComboBoxItemEditor(@NotNull AComboboxModel<? extends ItemKey> model, @NotNull TextResolver resolver) {
    super((AComboboxModel<ItemKey>) model);
    assert resolver != null;
    myResolver = resolver;
  }

  public static Detach install(AComboBox<? extends ItemKey> comboBox, TextResolver resolver) {
    ComboBoxItemEditor editor = new ComboBoxItemEditor(comboBox.getModel(), resolver);
    return editor.attach((AComboBox<ItemKey>) comboBox);
  }

  protected ItemKey createElementFromText(String text) {
    return myResolver.getItemKey(text);
  }

  protected boolean compareElementWithText(ItemKey artifact, String text) {
    return myResolver.isSameArtifact(artifact, text);
  }

  protected String getTextFromElement(@NotNull ItemKey object) {
    String displayName = object.getDisplayName();
    assert displayName != null : object;
    if (displayName == null)
      displayName = "";
    return displayName;
  }

  public static class MyUIController implements UIController<AComboBox<ItemKey>> {
    public MyUIController() {
    }

    public static void install(AComboBox<ItemKey> comboBox) {
      CONTROLLER.putClientValue(comboBox, new MyUIController());
    }

    public void connectUI(Lifespan lifespan, ModelMap model, AComboBox<ItemKey> component) {
      ItemModelKey key = model.getMetaInfo().findKey(component.getName());
      AComboboxModel cbModel = (AComboboxModel) key.getModel(lifespan, model, AComboboxModel.class);
      component.setModel(cbModel);
      Detach detach = ComboBoxItemEditor.install(component, key.getResolver());
      component.setEditable(true);
      component.setCanvasRenderer(DefaultUIController.ITEM_KEY_RENDERER);
      lifespan.add(detach);
    }
  }
}
