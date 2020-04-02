package com.almworks.explorer.workflow;

import com.almworks.api.application.*;
import com.almworks.api.dynaforms.AbstractWorkflowField;
import com.almworks.api.explorer.util.UserChooseController;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.AToolbarButton;
import com.almworks.util.components.completion.CompletingComboBoxController;
import com.almworks.util.images.Icons;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


/**
 * @author dyoma
 */
public class UserField implements AbstractWorkflowField<UserField.UserEditor> {
  private static final ItemKey ourDontChange =
    new ItemKeyStub("___dontchange__", WorkflowUtil.DONT_CHANGE, ItemOrder.NO_ORDER);

  private final ModelKey<ItemKey> myModelKey;
  private final DBAttribute<Long> myAttribute;
  private final BaseEnumConstraintDescriptor myEnumDescriptor;
  private final ScalarModel<ItemHypercube> mySourceCube;

  @Nullable
  private final List<ResolvedItem> myIncluded;
  @Nullable
  private final List<ResolvedItem> myExcluded;
  private final boolean myAllowNone;
  private final ItemKey myMeArtifact;

  private final ResolvedItem myNovalue;
  private InitialValuePolicy myInitialValue = ALLOW_NOT_CHANGE;

  public UserField(ModelKey<ItemKey> key, ResolvedItem novalue, BaseEnumConstraintDescriptor descriptor,
    DBAttribute<Long> attribute, BasicScalarModel<ItemHypercube> cube, List<ResolvedItem> included,
    List<ResolvedItem> excluded, boolean allowNone, ItemKey meArtifact)
  {
    myModelKey = key;
    myNovalue = novalue;
    myEnumDescriptor = descriptor;
    myAttribute = attribute;
    mySourceCube = cube;
    myIncluded = included;
    myExcluded = excluded;
    myAllowNone = allowNone;
    myMeArtifact = meArtifact;
  }

  public void addAffectedFields(List<DBAttribute> fieldList) {
    fieldList.add(myAttribute);
  }

  public void setValue(ItemUiModel model, UserEditor component)
    throws CantPerformExceptionExplained
  {
    component.stopEdit();
    ComboBoxField.setValue(model, myModelKey, component.getSelectedItem());
  }

  public String getSaveProblem(UserEditor component, MetaInfo metaInfo) {
    ItemKey item = component.getSelectedItem();
    assert item != null;
    boolean invalid = item == ItemKey.INVALID || item == null;
    if (!invalid)
      invalid = myIncluded != null && !myIncluded.contains(item);
    if (!invalid)
      invalid = myExcluded != null && myExcluded.contains(item);
    if (invalid)
      return "Please select valid " + myModelKey.getDisplayableName();
    return null;
  }

  public Pair<UserEditor, Boolean> createEditor(Lifespan lifespan, ChangeListener changeNotifier,
      MetaInfo metaInfo, List<? extends ItemWrapper> items, PropertyMap additionalProperties)
  {
    UserEditor editor = new UserEditor(myNovalue, myMeArtifact);
    AListModel<ItemKey> variants = WorkflowUtil.createEnumModel(lifespan, mySourceCube, myEnumDescriptor, null);
    if (myExcluded != null) {
      variants = FilteringListDecorator.exclude(lifespan, variants, myExcluded);
    }
    if (myIncluded != null) {
      variants = FilteringListDecorator.include(lifespan, variants, myIncluded);
    }
    variants = SortedListDecorator.create(lifespan, variants, ItemKey.COMPARATOR);
    if (myAllowNone) {
      variants = SegmentedListModel.prepend(myEnumDescriptor.getMissingItem(), variants);
    }
    ItemKey intialValue = myInitialValue.chooseValue(variants, items, myModelKey, myNovalue);
    editor.setValues(variants, intialValue, myEnumDescriptor.getMissingItem());
    editor.addListener(Lifespan.FOREVER, changeNotifier);
    return Pair.create(editor, myInitialValue.isEnabled(intialValue));
  }

  public NameMnemonic getLabel(MetaInfo metaInfo) {
    return NameMnemonic.parseString(myModelKey.getDisplayableName() + ":");
  }

  public boolean isInlineLabel() {
    return true;
  }

  public double getEditorWeightY() {
    return 0;
  }

  public JComponent getInitialFocusOwner(UserEditor component) {
    return component.getInitialFocusOwner();
  }

  public boolean isConsiderablyModified(UserEditor component) {
    return false;
  }

  public void enablePrimitive(UserEditor component, boolean enabled) {
    component.enableComponent(enabled);
  }

  public UserField forceChange() {
    myInitialValue = FORCE_CHANGE;
    return this;
  }

  private interface InitialValuePolicy {
    ItemKey chooseValue(AListModel<ItemKey> variants, List<? extends ItemWrapper> items,
      ModelKey<ItemKey> key, ResolvedItem novalue);

    @Nullable
    Boolean isEnabled(ItemKey value);
  }

  private static ItemKey getCommonValue(List<? extends ItemWrapper> items, ModelKey<ItemKey> key) {
    ItemKey select = null;
    for (ItemWrapper item : items) {
      ItemKey value = key.getValue(item.getLastDBValues());
      if (select == null)
        select = value;
      else if (!Util.equals(select, value)) {
        select = ourDontChange;
        break;
      }
    }
    return select != null ? select : ourDontChange;
  }

  private static final InitialValuePolicy ALLOW_NOT_CHANGE = new InitialValuePolicy() {
    public ItemKey chooseValue(AListModel<ItemKey> variants, List<? extends ItemWrapper> items,
      ModelKey<ItemKey> key, ResolvedItem novalue)
    {
      if (items.isEmpty()) {
        ItemKey any = ComboBoxField.getFirstMeaningfulValue(variants, novalue);
        return any != null ? any : ourDontChange;
      }
      return getCommonValue(items, key);
    }

    public Boolean isEnabled(ItemKey value) {
      return value != ourDontChange;
    }
  };

  private static final InitialValuePolicy FORCE_CHANGE = new InitialValuePolicy() {
    public ItemKey chooseValue(AListModel<ItemKey> variants, List<? extends ItemWrapper> items,
      ModelKey<ItemKey> key, ResolvedItem novalue)
    {
      return ItemKey.INVALID;
    }

    public Boolean isEnabled(ItemKey value) {
      return null;
    }
  };

  static class UserEditor extends JPanel implements ActionListener {
    private final CompletingComboBoxController<ItemKey> myController = new CompletingComboBoxController<ItemKey>();
    private final ResolvedItem myNovalue;
    private final ItemKey myMeArtifact;
    private final AToolbarButton mySetMe = new AToolbarButton();

    private UserEditor(ResolvedItem novalue, ItemKey meArtifact) {
      myNovalue = novalue;
      myMeArtifact = meArtifact;
      setLayout(new FormLayout("fill:d:g(1), 4dlu, fill:d", "pref"));
      mySetMe.setIcon(Icons.ACTION_SET_USER_ME);
      if (mySetMe != null)
        mySetMe.setToolTipText("Set to Me (" + myMeArtifact.getDisplayName() + ")");
      add(myController.getComponent(), new CellConstraints(1, 1));
      add(mySetMe, new CellConstraints(3, 1));
      mySetMe.addActionListener(this);
    }

    public ItemKey getSelectedItem() {
      return myController.getSelectedItem();
    }

    public void stopEdit() {
      myController.stopEdit();
    }

    public void enableComponent(boolean enabled) {
      ComboBoxField.enableComponent(myController.getComponent(), enabled, myController.getModel(), myNovalue);
      mySetMe.setEnabled(enabled);
    }

    public JComponent getInitialFocusOwner() {
      return myController.getComponent();
    }

    public void setValues(AListModel<ItemKey> variants, ItemKey intialValue, ResolvedItem missingArtifact) {
      myController.setVariantsModel(variants);
      myController.setSelectedItem(intialValue);
      UserChooseController.defaultSetup(myController, missingArtifact);
      myController.setFilterFactory(UserChooseController.DEFAULT_USER_FILTER);
      myController.getComponent().setMinimumSize(new Dimension(0, 0));
      mySetMe.setVisible(myMeArtifact != null);
    }

    public void addListener(Lifespan life, ChangeListener listener) {
      myController.getModel().addSelectionChangeListener(Lifespan.FOREVER, listener);
    }

    public void actionPerformed(ActionEvent e) {
      myController.getComponent().setSelectedItem(myMeArtifact);
    }
  }
}
