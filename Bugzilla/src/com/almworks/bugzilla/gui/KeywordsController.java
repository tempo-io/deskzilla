package com.almworks.bugzilla.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.EnumConstraintType;
import com.almworks.api.application.util.BaseKeyBuilder;
import com.almworks.api.application.util.BaseModelKey;
import com.almworks.api.application.viewer.UIController;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.JointChangeListener;
import com.almworks.util.components.*;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public class KeywordsController implements UIController<FieldWithMoreButton> {
  private final ModelKey<? extends Collection<ItemKey>> myModelKey;

  static void setup(final ModelKey<? extends Collection<ItemKey>> modelKey, FieldWithMoreButton field) {
    UIController.CONTROLLER.putClientValue(field, new KeywordsController(modelKey));
  }

  private KeywordsController(ModelKey<? extends Collection<ItemKey>> modelKey) {
    myModelKey = modelKey;
  }

  public void connectUI(
    @NotNull final Lifespan lifespan, @NotNull final ModelMap modelMap,
    @NotNull final FieldWithMoreButton component)
  {
    if(lifespan.isEnded()) {
      return;
    }

    setupTextField(component);

    final PlainDocument document = myModelKey.getModel(lifespan, modelMap, PlainDocument.class);
    UIUtil.setDocument(lifespan, (JTextField)component.getField(), document);

    setupMoreAction(lifespan, modelMap, component, document);
  }

  private void setupTextField(FieldWithMoreButton field) {
    final JTextField textField = new JTextField();
    textField.setColumns(15);
    field.setField(textField);
    field.setDoubleClickEnabled(true);
  }

  private void setupMoreAction(
    Lifespan lifespan, ModelMap model, final FieldWithMoreButton component, PlainDocument document)
  {
    final Lifecycle popUpCycle = new Lifecycle();
    lifespan.add(popUpCycle.getDisposeDetach());
    component.setAction(new KeywordsDropDown(component, myModelKey, model, popUpCycle, document));
    lifespan.add(new Detach() {
      @Override
      protected void doDetach() throws Exception {
        component.setAction(null);
      }
    });
  }
}

/**
 * The action that displays a drop-down checkbox list
 * with Bugzilla keywords to choose from.
 */
class KeywordsDropDown extends DropDownListener.ForComponent {
  private final FieldWithMoreButton myComponent;
  private final ModelKey<? extends Collection<ItemKey>> myModelKey;
  private final ModelMap myModelMap;
  private final Lifecycle myPopUpCycle;
  private final PlainDocument myDocument;
  private ACheckboxList<ItemKey> myList;

  public KeywordsDropDown(
    FieldWithMoreButton component, ModelKey<? extends Collection<ItemKey>> modelKey,
    ModelMap modelMap, Lifecycle popUpCycle, PlainDocument document)
  {
    super(component);
    myComponent = component;
    myModelKey = modelKey;
    myModelMap = modelMap;
    myPopUpCycle = popUpCycle;
    myDocument = document;
  }

  protected JComponent createPopupComponent() {
    final Lifespan life = myPopUpCycle.lifespan();

    final AListModel<ItemKey> variantsModel = getVariantsModel(life);
    if(variantsModel == null) {
      return null;
    }

    final AListModel<ItemKey> valueModel = getValueModel(life);
    assert valueModel != null;

    myList = createList(variantsModel, valueModel);
    setupMutualUpdates(life, valueModel, myList, myDocument);
    return createEnclosingScrollPane(myList);
  }

  private AListModel<ItemKey> getVariantsModel(Lifespan life) {
    // :kludge:
    final EnumConstraintType type =
      ((BaseKeyBuilder.RefCollectionIO)((BaseModelKey)myModelKey).getIO()).getConstraintType();
    if(type == null) {
      return null;
    }

    LoadedItemServices lis = LoadedItemServices.VALUE_KEY.getValue(myModelMap);
    if (lis == null) return null;
    final VariantsModelFactory factory = lis.getVariantsFactory(type);
    final AListModel<ItemKey> variantsModel = factory.createModel(life);
    if(variantsModel.getSize() <= 0) {
      return null;
    }

    return variantsModel;
  }

  private AListModel getValueModel(Lifespan life) {
    return myModelKey.getModel(life, myModelMap, AListModel.class);
  }

  private ACheckboxList<ItemKey> createList(
    AListModel<ItemKey> variantsModel,
    AListModel<ItemKey> valueModel)
  {
    final ACheckboxList<ItemKey> list = new ACheckboxList<ItemKey>(variantsModel);
    list.getCheckedAccessor().setSelected(valueModel.toList());
    return list;
  }

  private void setupMutualUpdates(Lifespan life,
    final AListModel<ItemKey> valueModel,
    final ACheckboxList<ItemKey> list,
    final Document document)
  {
    final SelectionAccessor<ItemKey> accessor = list.getCheckedAccessor();
    final boolean[] update = { false };

    accessor.addAWTChangeListener(life, new JointChangeListener(update) {
      @Override
      protected void processChange() {
        updateDocument(document, accessor.getSelectedItems());
      }
    });

    valueModel.addAWTChangeListener(life, new JointChangeListener(update) {
      protected void processChange() {
        accessor.setSelected(valueModel.toList());
      }
    });
  }

  private JScrollPane createEnclosingScrollPane(ACheckboxList<ItemKey> list) {
    JScrollPane scrollPane = UIUtil.encloseWithScrollPaneForPopup(list, myComponent);
    adjustBackgroundColor(scrollPane, list);
    return scrollPane;
  }

  private void adjustBackgroundColor(JScrollPane scrollPane, ACheckboxList<ItemKey> list) {
    final Color bg = ColorUtil.between(UIUtil.getEditorBackground(), Color.YELLOW, 0.1F);
    scrollPane.setBackground(bg);
    scrollPane.getViewport().setBackground(bg);
    list.setBackground(bg);
    list.getScrollable().setBackground(bg);
  }

  private void updateDocument(Document document, List<ItemKey> selectedItems) {
    final String oldText = Util.NN(DocumentUtil.getDocumentText(document)).trim();

    final List<String> keywords = Collections15.arrayList(TextUtil.getKeywords(oldText));
    final List<String> ids = ItemKey.GET_ID.collectList(selectedItems);
    keywords.retainAll(ids);

    for(String id : ids) {
      id = id.trim();
      if(id.length() > 0 && !keywords.contains(id)) {
        keywords.add(id);
      }
    }

    final String newText = TextUtil.separate(keywords, " ");
    if(!newText.equalsIgnoreCase(oldText)) {
      DocumentUtil.setDocumentText(document, newText);
    }
  }

  protected void onDropDownHidden() {
    UIUtil.requestFocusLater(myComponent.getField());
    myPopUpCycle.cycle();
  }

  @Override
  protected void onDropDownShown() {
    final ACheckboxList<ItemKey> list = myList;
    if(list != null) {
      UIUtil.requestFocusLater(list.getSwingComponent());
      list.getSelectionAccessor().ensureSelectionExists();
    }
  }
}
