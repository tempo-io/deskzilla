package com.almworks.explorer;

import com.almworks.api.application.LoadedItem;
import com.almworks.util.collections.Convertor;
import com.almworks.util.components.FlatCollectionComponent;
import com.almworks.util.ui.actions.AnAction;
import com.almworks.util.ui.actions.PresentationMapping;
import com.almworks.util.ui.actions.presentation.*;
import com.almworks.util.ui.swing.SwingTreeUtil;
import org.almworks.util.Collections15;

import java.util.LinkedHashSet;
import java.util.List;

public class IssueDrivenPopupEntry<T> implements PopupEntry {
  public static final IssueDrivenPopupEntry<LoadedItem> ENTRY =
    new IssueDrivenPopupEntry<LoadedItem>(Convertor.<LoadedItem>identity());

  private final Convertor<T, LoadedItem> myConvertor;

  public IssueDrivenPopupEntry(Convertor<T, LoadedItem> convertor) {
    myConvertor = convertor;
  }

  public void addToPopup(ActionGroupVisitor component) {
    final FlatCollectionComponent<T> table =
      SwingTreeUtil.findAncestorOfType(component.getContextComponent(), FlatCollectionComponent.class);
    assert table != null : component.getContextComponent();

    final List<T> items = table.getSelectionAccessor().getSelectedItems();
    final LinkedHashSet<AnAction> actions = Collections15.linkedHashSet();
    for(final T item : items) {
      actions.addAll(myConvertor.convert(item).getActions());
    }

    MenuBuilder.addActions(component, actions.iterator(), PresentationMapping.VISIBLE_ONLY_IF_ENABLED);
  }
}
