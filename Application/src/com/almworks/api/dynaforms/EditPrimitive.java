package com.almworks.api.dynaforms;

import com.almworks.api.application.*;
import com.almworks.util.Pair;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.model.ValueModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

public interface EditPrimitive<C extends JComponent> {
  EditPrimitive SPACE = new PseudoEditPrimitive("SPACE");
  /**
   * Edit primitives should use this model to report their enabled/disabled state, if there is such need.
   * This is an optional attribute communicated through additional properties map.
   */
  TypedKey<ValueModel<Boolean>> ENABLED_MODEL = TypedKey.create("enabledModel");

  void setValue(ItemUiModel model, C component) throws CantPerformExceptionExplained;

  @Nullable
  String getSaveProblem(C component, MetaInfo metaInfo);

  NameMnemonic getLabel(MetaInfo metaInfo) throws CantPerformExceptionExplained;

  /**
   * Creates and returns editor for this primitive. If the boolean in the pair is true, it means that this primitive
   * is initially enabled.
   *
   * @param lifespan
   * @param changeNotifier
   * @param metaInfo
   * @param items
   * @param additionalProperties additional communication between edit primitives and editor (for latter see e.g. #ENABLED_MODEL)
   * @return
   */
  @Nullable
  Pair<C, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties)
    throws CantPerformExceptionExplained;

  boolean isInlineLabel();

  @Nullable
  JComponent getInitialFocusOwner(C component);

  boolean isConsiderablyModified(C component);

  void enablePrimitive(C component, boolean enabled);

  double getEditorWeightY();


  // kludge - not enough polymorphic
  public static class PseudoEditPrimitive implements EditPrimitive {
    private final String myName;

    public PseudoEditPrimitive(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }

    public void setValue(ItemUiModel model, JComponent component) throws CantPerformExceptionExplained {
    }

    public String getSaveProblem(JComponent component, MetaInfo metaInfo) {
      return null;
    }

    public Pair createEditor(Lifespan lifespan, ChangeListener changeNotifier, MetaInfo metaInfo, List items, PropertyMap additionalProperties) {
      return null;
    }

    public NameMnemonic getLabel(MetaInfo metaInfo) {
      return null;
    }

    public boolean isInlineLabel() {
      return true;
    }

    public JComponent getInitialFocusOwner(JComponent component) {
      return null;
    }

    public boolean isConsiderablyModified(JComponent component) {
      return false;
    }

    public void enablePrimitive(JComponent component, boolean enabled) {
    }

    public double getEditorWeightY() {
      return 0;
    }
  }

  public static class Separator extends PseudoEditPrimitive {
    public Separator(String name) {
      super(name);
    }
  }
}
