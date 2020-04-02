package com.almworks.api.actions;

import com.almworks.actions.ConfirmEditDialog;
import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.engine.Engine;
import com.almworks.api.gui.DialogBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.explorer.loader.ItemUiModelImpl;
import com.almworks.integers.*;
import com.almworks.items.sync.SyncManager;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.collections.MultiMap;
import com.almworks.util.commons.Factory;
import com.almworks.util.components.PlaceHolder;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.StringUtil;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

import static org.almworks.util.Collections15.hashMap;

/**
 * @author dyoma
 */
public class ItemActionUtils {
  public static final String COMMIT_NAME = "Upload";
  public static final String COMMIT_TOOLTIP = "Save changes and upload to server";
  public static final String SAVE_NAME = "Save Draft";
  public static final String SAVE_TOOLTIP = "Save changes to the local database without uploading";
  public static final String CANCEL_NAME = "Cancel";
  public static final String CANCEL_TOOLTIP = "Discard changes and close window";

  public static final DataRole<PlaceHolder> ITEM_EDIT_WINDOW_TOOLBAR_RIGHT_PANEL =
    DataRole.createRole(PlaceHolder.class);

  public static List<ItemWrapper> basicUpdate(@NotNull UpdateContext context) throws CantPerformException {
    watchItemsCollection(context);
    List<ItemWrapper> collection = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    for (ItemWrapper wrapper : collection) {
      if (wrapper.services().isDeleted())
        throw new CantPerformException();
      Connection connection = wrapper.getConnection();
      if (connection == null || connection.getState().getValue().isDegrading()) {
        // Connection is degraded
        throw new CantPerformException();
      }
    }
    return collection;
  }

  public static void watchItemsCollection(@NotNull UpdateContext context) {
    // todo #822 (http://bugzilla/main/show_bug.cgi?id=822) watch only one
    context.watchRole(ItemWrapper.ITEM_WRAPPER);
    context.watchRole(LoadedItem.LOADED_ITEM);
  }

  @NotNull
  public static ItemUiModelImpl getModel(@NotNull ActionContext context) throws CantPerformException {
    return context.getSourceObject(ItemUiModelImpl.ROLE);
  }

  public static ItemUiModelImpl getModel(ItemWrapper wrapper) {
    return (wrapper instanceof ItemUiModelImpl) ? (ItemUiModelImpl) wrapper :
      ItemUiModelImpl.create(wrapper);
  }

  public static void updateLock(UpdateContext context, @Nullable DataRole<? extends UiItem> uiItemRole) throws CantPerformException {
    if (uiItemRole == null) return;
    context.watchModifiableRole(SyncManager.MODIFIABLE);
    List<UiItem> items = context.getSourceCollection(uiItemRole);
    checkNotLocked(context, items);
  }

  public static void checkNotLocked(ActionContext context, Collection<? extends UiItem> items) throws CantPerformException {
    final SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    for(UiItem item : items) {
      checkNotLocked(syncMan, item);
    }
  }

  public static void checkNotLocked(SyncManager locker, UiItem item) throws CantPerformException {
    checkNotLocked(locker, item.getItem());
  }

  public static void checkNotLocked(ActionContext context, long item) throws CantPerformException {
    checkNotLocked(context.getSourceObject(SyncManager.ROLE), item);
  }

  public static void checkNotLocked(ActionContext context, LongList items) throws CantPerformException {
    final SyncManager syncMan = context.getSourceObject(SyncManager.ROLE);
    if (syncMan.findAnyLock(items) != null) throw new CantPerformException();
  }

  public static void checkNotLocked(SyncManager locker, long item) throws CantPerformException {
    if(locker.findLock(item) != null) {
      throw new CantPerformException();
    }
  }

  public static void checkCanEdit(ActionContext context, long item, long connectionItem) throws CantPerformException {
    checkNotLocked(context, item);
    checkConnectionEdit(context, connectionItem);
  }

  public static void checkConnectionEdit(ActionContext context, long connectionItem) throws CantPerformException {
    Connection connection = getConnection(context, connectionItem);
    if (!connection.hasCapability(Connection.Capability.EDIT_ITEM)) throw new CantPerformException();
  }

  @NotNull
  public static Connection getConnection(ActionContext context, long connectionItem) throws CantPerformException {
    Connection connection = context.getSourceObject(Engine.ROLE).getConnectionManager().findByItem(connectionItem);
    return CantPerformException.ensureNotNull(connection);
  }

  public static <C extends Connection> C getConnection(ActionContext context, long connectionItem, Class<? extends C> clazz)
    throws CantPerformException
  {
    return CantPerformException.cast(clazz, getConnection(context, connectionItem));
  }

  public static MultiMap<Connection, ItemWrapper> getItemWrappersGroupedByConnection(ActionContext context)
    throws CantPerformException
  {
    List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    MultiMap<Connection, ItemWrapper> result = MultiMap.create();
    for (ItemWrapper wrapper : wrappers) {
      if (wrapper.services().isDeleted())
        continue;
      Connection connection = wrapper.getConnection();
      if (connection != null && !connection.getState().getValue().isDegrading()) {
        result.add(connection, wrapper);
      }
    }
    return result;
  }

  public static Map<Connection, LongList> getItemsGroupedByConnection(ActionContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    Map<Connection, LongList> result = hashMap();
    for (ItemWrapper wrapper : wrappers) {
      LoadedItemServices services = wrapper.services();
      if (services.isDeleted())
        continue;
      Connection connection = wrapper.getConnection();
      if (connection != null && !connection.getState().getValue().isDegrading()) {
        WritableLongList target = (WritableLongList) result.get(connection);
        if (target == null) {
          target = new LongArray();
          result.put(connection, target);
        }
        target.add(services.getItem());
      }
    }
    return result;
  }

  @NotNull
  public static <T extends Connection> T getSingleConnection(Collection<? extends ItemWrapper> items, Class<T> clazz)
    throws CantPerformException
  {
    Connection connection = null;
    for (ItemWrapper item : items) {
      Connection c = item.getConnection();
      if (c == null)
        throw new CantPerformException(item + ": no connection");
      if (connection == null) {
        connection = c;
      } else if (!Util.equals(connection, c)) {
        throw new CantPerformException(connection + " " + c + " " + items);
      }
    }
    if (connection == null) {
      throw new CantPerformException(String.valueOf(items));
    }
    if (!clazz.isInstance(connection)) {
      throw new CantPerformException(clazz + " " + connection + " " + items);
    }
    return (T) connection;
  }

  public static AnAction setupCancelEditAction(SimpleAction action) {
    action.setDefaultPresentation(PresentationKey.NAME, L.actionName(CANCEL_NAME));
    action.setDefaultPresentation(PresentationKey.SMALL_ICON, Icons.ACTION_GENERIC_CANCEL_OR_REMOVE);
    action.setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
    action.setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip(CANCEL_TOOLTIP));
    return action;
  }

  public static AnAction setupCommitAction(SimpleAction action) {
    action.setDefaultPresentation(PresentationKey.NAME, L.actionName(COMMIT_NAME));
    action.setDefaultPresentation(PresentationKey.SMALL_ICON, Icons.ACTION_COMMIT_ARTIFACT);
    action.setDefaultPresentation(PresentationKey.ENABLE, EnableState.DISABLED);
    action.setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip(COMMIT_TOOLTIP));
    return action;
  }

  public static AnAction setupSaveAction(SimpleAction action) {
    action.setDefaultPresentation(PresentationKey.NAME, L.actionName(SAVE_NAME));
    action.setDefaultPresentation(PresentationKey.SMALL_ICON, Icons.ACTION_SAVE);
    action.setDefaultPresentation(PresentationKey.ENABLE, EnableState.DISABLED);
    action.setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION,
      L.tooltip(SAVE_TOOLTIP));
    return action;
  }

  /**
   * @return value, if it is the same for every artifact, or null
   */
  @Nullable
  public static <T> T getSameForAllKeyValue(ModelKey<T> key, List<ItemWrapper> wrappers) {
    T commonValue = null;
    PropertyMap firstValues = null;
    for (ItemWrapper wrapper : wrappers) {
      if (firstValues == null) {
        firstValues = wrapper.getLastDBValues();
        commonValue = wrapper.getModelKeyValue(key);
      } else {
        if (!key.isEqualValue(wrapper.getLastDBValues(), firstValues)) {
          return null;
        }
      }
    }
    return commonValue;
  }

  /**
   * NB: this method enables context if it satisfies its conditions. Be sure that you do not disable context before calling this method, since disable will be overridden.
   */
  public static ItemWrapper updateForEditSingleItem(UpdateContext context) throws CantPerformException {
    return updateForEditSingleItem(context, ItemWrapper.ITEM_WRAPPER);
  }

  /**
   * NB: this method enables context if it satisfies its conditions. Be sure that you do not disable context before calling this method, since disable will be overridden.
   */
  public static ItemWrapper updateForEditSingleItem(UpdateContext context, @Nullable DataRole<? extends UiItem> lockRole) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    List<ItemWrapper> wrappers = basicUpdate(context);
    updateLock(context, lockRole);
    ItemWrapper item = CantPerformException.ensureSingleElement(wrappers);
    DBDataRoles.setEnabledIfConnectionCapableOf(context, item.getConnection(), Connection.Capability.EDIT_ITEM);
    return item;
  }

  /**
   * NB: this method enables context if it satisfies its conditions. Be sure that you do not disable context before calling this method, since disable will be overridden.
   */
  public static List<ItemWrapper> updateForEditMultipleItems(UpdateContext context) throws CantPerformException {
    return updateForEditMultipleItems(context, ItemWrapper.ITEM_WRAPPER);
  }

  /**
   * NB: this method enables context if it satisfies its conditions. Be sure that you do not disable context before calling this method, since disable will be overridden.
   */
  public static List<ItemWrapper> updateForEditMultipleItems(UpdateContext context, @Nullable DataRole<? extends UiItem> lockRole) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    context.setEnabled(false);
    List<ItemWrapper> wrappers = basicUpdate(context);
    updateLock(context, lockRole);
    for (ItemWrapper wrapper : wrappers) {
      Connection connection = wrapper.getConnection();
      if (connection == null || !connection.hasCapability(Connection.Capability.EDIT_ITEM)) {
        throw new CantPerformException();
      }
    }
    context.setEnabled(true);
    return wrappers;
  }

  public static Connection getCommonConnection(List<? extends ItemWrapper> items) {
    if (items == null || items.isEmpty())
      return null;
    Iterator<? extends ItemWrapper> it = items.iterator();
    Connection result = it.next().getConnection();
    if (result == null)
      return null;
    while (it.hasNext()) {
      ItemWrapper item = it.next();
      if (!Util.equals(item.getConnection(), result))
        return null;
    }
    return result;
  }

  /**
   * Verifies result of editing artifact.
   * If user attempts to upload the result, all local changes will be verified.
   * If not, only changes made during the last edit will be verified.
   * If verification fails, shows a dialog to user via the specified dialog builder with two options: save changes locally
   *  and continue editing. If user selects 'continue', throws CantPerformExceptionSilently, in case of other choice returns false.
   * @param upload specifies that the user wants to upload changes
   * @param confirmBuilder
   * @return true iff verification succeeds, false otherwise
   * @throws CantPerformExceptionSilently if user chooses to continue editing
   */
  public static boolean verifyLastArtifactEdit(ItemUiModelImpl artifactModel, boolean upload, Factory<DialogBuilder> confirmBuilder) throws CantPerformExceptionSilently {
    StringBuilder errors = getLastEditErrors(artifactModel);
    if (errors != null) {
      ConfirmEditDialog.Result res = ConfirmEditDialog.show(confirmBuilder.create(), upload, errors.toString(), false);
      if (res.isContinueEdit()) {
        throw new CantPerformExceptionSilently("User chose to continue editing");
      }
      return res.isUpload();
    }
   return true;
  }

  /**
   * Verifies artifact changes against model key verifiers and returns errors, if any.
   * If user attempts to upload the result, all local changes will be verified.
   * If not, only changes made during the last edit will be verified.
   * @return StringBuilder containing errors or null if there were no
   */
  @Nullable
  public static StringBuilder getLastEditErrors(ItemUiModelImpl artifactModel) {
    Collection<ModelKeyVerifier> verifiers = artifactModel.getMetaInfo().getVerifierManager().getVerifiers();

    PropertyMap newValues = artifactModel.takeSnapshot();
    PropertyMap oldValues = artifactModel.getLastDBValues();

    StringBuilder errors = null;
    String sep = "";
    for (ModelKeyVerifier ver : verifiers) {
      if (ver == null) { assert false; continue; }
      // JCO-696: decided to always verify the last edit only
      String err = ver.verifyEdit(oldValues, newValues);
      if (err != null) {
        //noinspection ConstantConditions
        errors = TextUtil.append(errors, sep).append(err);
        sep = StringUtil.LINE_SEPARATOR;
      }
    }
    return errors;
  }

  public static Factory<DialogBuilder> getDialogBuilder(final ActionContext context, final String dialogId) throws CantPerformException {
    final DialogManager dialogManager = context.getSourceObject(DialogManager.ROLE);
    return new Factory<DialogBuilder>() {
      @Override
      public DialogBuilder create() {
        return dialogManager.createBuilder(dialogId);
      }
    };
  }

  public static void installItemModelProvider(JComponent component, ItemUiModelImpl model) {
    ConstProvider.addRoleValue(component, ItemUiModelImpl.ROLE, model);
  }

  public static LongArray collectItems(Collection<? extends UiItem> items) {
    if (items == null || items.isEmpty()) return new LongArray();
    LongArray result = new LongArray();
    for (UiItem item : items) result.add(item.getItem());
    return result;
  }

  public static MetaInfo getUniqueMetaInfo(List<? extends ItemWrapper> items) throws CantPerformException {
    MetaInfo metaInfo = null;
    for (ItemWrapper wrapper : items) {
      MetaInfo currentMeta = wrapper.getMetaInfo();
      if (metaInfo == null) {
        metaInfo = currentMeta;
      } else if (metaInfo != currentMeta) {
        throw new CantPerformException(Local.parse("Can't apply to different types of " + Terms.ref_artifacts));
      }
    }
    return metaInfo;
  }
}