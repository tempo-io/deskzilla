package com.almworks.api.actions;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.MetaInfo;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class StdItemActions {
  public static final AnAction ADD_COMMENT = new StdAction(MetaInfo.ADD_COMMENT);
  public static final AnAction EDIT_COMMENT = new StdAction(MetaInfo.EDIT_COMMENT);
  public static final AnAction REPLY_TO_COMMENT = new StdAction(MetaInfo.REPLY_TO_COMMENT);
  public static final AnAction ATTACH_FILE = new StdAction(MetaInfo.ATTACH_FILE);
  public static final AnAction ATTACH_SCREENSHOT = new StdAction(MetaInfo.ATTACH_SCREENSHOT);
  public static final AnAction AUTO_ASSIGN = new StdAction(MetaInfo.AUTO_ASSIGN);
  public static final AnAction REMOVE_COMMENT = new StdAction(MetaInfo.REMOVE_COMMENT);
  public static final Map<PresentationKey, Object> STD_AUTO_ASSIGN_PRESENTATION;

  static {
    Map<PresentationKey, Object> map = Collections15.hashMap();
    map.put(PresentationKey.SMALL_ICON, Icons.ACTION_ASSIGN);
    map.put(PresentationKey.NAME, "Auto Assign");
    STD_AUTO_ASSIGN_PRESENTATION = Collections.unmodifiableMap(map);
  }

  public static void registerActions(ActionRegistry registry) {
    registerAction(registry, ADD_COMMENT);
  }

  private static void registerAction(ActionRegistry registry, AnAction ... actions) {
    for (AnAction action : actions) {
      assert action instanceof StdAction : action;
      ((StdAction) action).register(registry);
    }
  }

  private static class StdAction extends DelegatingAction {
    private final String myId;

    public StdAction(String id) {
      myId = id;
    }

    public void update(UpdateContext context) throws CantPerformException {
      context.watchRole(ItemWrapper.ITEM_WRAPPER);
      IdActionProxy.setShortcut(context, myId);
      try {
        super.update(context);
      } catch (CantPerformException ex) {
        context.setEnabled(EnableState.INVISIBLE);
        throw ex;
      }
    }

    @Nullable
    protected AnAction getDelegate(ActionContext context) throws CantPerformException {
      List<ItemWrapper> items = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
      MetaInfo common = null;
      for (ItemWrapper item : items) {
        MetaInfo metaInfo = item.getMetaInfo();
        if (common == null)
          common = metaInfo;
        else if (!common.equals(metaInfo))
          throw new CantPerformException();
      }
      common = CantPerformException.ensureNotNull(common);
      return common.getStdAction(myId, context);
    }

    public void register(ActionRegistry registry) {
      registry.registerAction(myId, this);
    }
  }
}
