package com.almworks.bugzilla.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ModelMap;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.explorer.gui.ItemsListKey;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.util.advmodel.OrderListModel;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifespan;

import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.util.List;
import java.util.Set;

/**
 * @author dyoma
 */
class BugListController implements UIController<JTextComponent> {
  private final ItemsListKey myKey;

  public BugListController(ItemsListKey key) {
    myKey = key;
  }

  public static void install(JTextComponent component, ItemsListKey key) {
    CONTROLLER.putClientValue(component, new BugListController(key));
  }

  public void connectUI(Lifespan lifespan, ModelMap modelMap, JTextComponent component) {
    OrderListModel<ItemKey> model = myKey.getModel(lifespan, modelMap, OrderListModel.class);
    TextConvertor convertor = new TextConvertor(model, myKey.getResolver(), modelMap, myKey);
    component.setDocument(convertor.getDocument());
    convertor.listenDocument(lifespan);
  }

  private static class TextConvertor extends DocumentAdapter {
    private static final String BUG_SEPARATOR = "(\\s|,)+";
    private final Set<ItemKey> myInitialKeys;
    private final OrderListModel<ItemKey> myModel;
    private final ModelMap myModelMap;
    private final ItemsListKey myKey;
    private final PlainDocument myDocument = new PlainDocument();
    private final TextResolver myResolver;

    public TextConvertor(OrderListModel<ItemKey> model, TextResolver resolver, ModelMap modelMap, ItemsListKey key) {
      myModel = model;
      myModelMap = modelMap;
      myKey = key;
      myInitialKeys = Collections15.hashSet(myModel.toList());
      DocumentUtil.setDocumentText(myDocument, getStringPresentation(model.toList()));
      myResolver = resolver;
    }

    public Document getDocument() {
      return myDocument;
    }

    public void listenDocument(Lifespan lifespan) {
      DocumentUtil.addListener(lifespan, myDocument, this);
    }

    protected void documentChanged(DocumentEvent e) {
      List<ItemKey> keys = parseList(DocumentUtil.getDocumentText(myDocument));
      myKey.replaceValues(myModelMap, keys);
    }

    private List<ItemKey> parseList(String text) {
      if (text == null || text.trim().length() == 0)
        return Collections15.emptyList();
      String[] bugIds = text.split(BUG_SEPARATOR);
      List<ItemKey> result = Collections15.arrayList();
      for (String id : bugIds) {
        ItemKey existing = ItemKey.DISPLAY_NAME.detectEqual(myInitialKeys, id);
        if (existing != null)
          result.add(existing);
        else {
          ItemKey inModel = ItemKey.DISPLAY_NAME.detectEqual(myModel.toList(), id);
          if (inModel != null)
            result.add(inModel);
          else
            result.add(myResolver.getItemKey(id));
        }
      }
      return result;
    }

    private static String getStringPresentation(List<? extends ItemKey> items) {
      return TextUtil.separate(items, ", ", ItemKey.DISPLAY_NAME);
    }
  }
}
