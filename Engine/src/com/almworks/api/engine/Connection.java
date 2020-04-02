package com.almworks.api.engine;

import com.almworks.api.application.*;
import com.almworks.api.application.order.Order;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.util.Pair;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.commons.Procedure;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.model.*;
import com.almworks.util.models.TableColumnAccessor;
import com.almworks.util.threads.*;
import com.almworks.util.ui.UIComponentWrapper;
import com.almworks.util.ui.actions.AnAction;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author sereda
 */
public interface Connection {
  DBAttribute<Long> USER = Engine.NS.subNs("connection").link("user", "User", false);

  ReadonlyConfiguration getConfiguration();

  void update(ReadonlyConfiguration configuration) throws ConfigurationException;

  ConnectionSynchronizer getConnectionSynchronizer();

  public ConnectionContext getContext();

  ItemProvider getProvider();

  ConnectionViews getViews();

  // todo remove this method - but provide a way to connect bugzilla gui classes to bugzilla provider
  // todo :refactoring: observation: returned container delegates to getContext().getContainer(); remove that also?
  ComponentContainer getConnectionContainer();

  UIComponentWrapper getConnectionStateComponent();

  SetHolder<SyncTask> getSyncTasks();

  ScalarModel<InitializationState> getInitializationState();

  void requestReinitialization();

  /**
   * Call only for READY connection.
   */
  long getConnectionItem();

  /**
   * Can always be called.
   */
  DBIdentifiedObject getConnectionRef();

  String getConnectionID();

  Collection<DBItemType> getPrimaryTypes();

  long getPrototypeItem(DBItemType itemType);

  boolean hasCapability(Capability capability);

  @NotNull
  BasicScalarModel<AutoSyncMode> getAutoSyncMode();

  @Nullable
  CollectionModel<RemoteQuery> getRemoteQueries();

  @Nullable
  ScalarModel<Collection<RemoteQuery2>> getRemoteQueries2();

  /** Removes connection and clears all connection items.
   * After corresponding write transaction finishes successfully, runs the callback in AWT. */
  void removeConnection(@NotNull Runnable onConnectionItemsCleared);

  void startConnection();

  void stopConnection();

  /**
   * In state {@link ConnectionState#READY} connection item (see {@link #getConnectionItem()}) must be materialized.
   */
  ScalarModel<ConnectionState> getState();

  SyncTask synchronizeItemView(Constraint validRemoteQueryConstraint, @Nullable DBFilter view,
    @Nullable LongList localResult, String queryName, Procedure<SyncTask> runFinally);

  void uploadItems(LongList items);

  @ThreadAWT
  @Nullable
  SyncTask downloadItemDetails(LongList items);

  @Nullable
  String buildDefaultQueriesXML();

  @Nullable
  @CanBlock
  QueryUrlInfo getQueryURL(Constraint constraint, DBReader reader) throws InterruptedException;

  @ThreadAWT
  @NotNull
  Pair<DBFilter, Constraint> getViewAndConstraintForUrl(String url) throws CantPerformExceptionExplained;

  /**
   * @return ItemSource that delivers items with URLs from the specified Iterable. If any of the URLs is not recognized by this connection, this method returns null.
   * If connection is not ready, returns null.
   * */
  @ThreadAWT
  @Nullable
  ItemSource getItemSourceForUrls(Iterable<String> urls);

  @Nullable
  @CanBlock
  String getItemUrl(ItemVersion localVersion);

  @ThreadAWT
  boolean isItemUrl(String itemUrl);

  @ThreadAWT @Nullable
  String getItemIdForUrl(String itemUrl);

  @Deprecated
  @CanBlock
  @Nullable
  Date getItemTimestamp(ItemVersion version);

  @Deprecated
  @CanBlock
  @Nullable
  String getItemShortDescription(ItemVersion version);

  @Deprecated
  @CanBlock
  @Nullable
  String getItemLongDescription(ItemVersion version);

  @Deprecated
  @CanBlock
  @Nullable
  String getItemId(ItemVersion version);

  @Deprecated
  @CanBlock
  @Nullable
  String getItemSummary(ItemVersion version);

  @Nullable
  @CanBlock
  String getExternalIdSummaryString(ItemVersion version);

  @Nullable
  @CanBlock
  String getExternalIdString(ItemVersion version);

  @Nullable
  @ThreadSafe
  String getDisplayableItemId(ItemWrapper wrapper);

  /**
   * @return loaded attachments for the specified primary item
   */
  @CanBlock
  @NotNull
  Collection<? extends Attachment> getItemAttachments(ItemVersion primaryItem);

  /**
   * Main model of columns applicable to this Connection. Does not contain auxiliary columns (distinction of main and auxiliary columns is implementation-specific).
   * To get all columns, unite the model returned by this method with the model returned by {@link #getAuxiliaryColumns()}.
   * Should return the same instance.
   */
  @ThreadAWT
  AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getMainColumns();

  /**
   * Comparator for columns returned by {@link #getMainColumns}
   */
  @NotNull
  Comparator<? super TableColumnAccessor<LoadedItem, ?>> getMainColumnsOrder();

  /**
   * Auxiliary columns applicable to this Connection. Which columns are auxiliary is specific to the implementation.
   * To get all columns, unite the model returned by this method with the model returned by {@link #getMainColumns()}.
   * Should return the same instance.
   * @return null means "implementation does not contemplate any auxiliary columns at all"
   */
  @ThreadAWT
  @Nullable(documentation = "implementation does not contemplate any auxiliary columns at all")
  AListModel<? extends TableColumnAccessor<LoadedItem, ?>> getAuxiliaryColumns();

  /**
   * Comparator for columns returned by {@link #getAuxiliaryColumns}
   */
  @Nullable(documentation = "if #getAuxiliaryColumns is null")
  Comparator<? super TableColumnAccessor<LoadedItem, ?>> getAuxColumnsOrder();

  /**
   * Model of all orders applicable to this Connection. Should return the same instance.
   */
  @ThreadAWT
  AListModel<? extends Order> getOrdersModel();

  @NotNull
  AListModel<? extends ConstraintDescriptor> getDescriptors();

  boolean matchAllWords(long item, char[][] charWords, String[] stringWords, DBReader reader);

  boolean isAllowedByLicense();

  @Nullable
  @ThreadSafe
  ConstraintDescriptor getDescriptorByIdSafe(String id);

  @ThreadAWT
  @Nullable
  public AnAction createDropChangeAction(ItemHypercube target, String frameId, boolean move);

  /**
   * Returns true iff the connection does not have a valid license state (synchronization is not allowed).
   */
  boolean isOffline();
  
  /**
   * Adjusts the specified hypercube to additional connection limitations (such as products/projects for which this connection is configured).
   */
  @ThreadAWT
  ItemHypercube adjustHypercube(@NotNull ItemHypercube hypercube);

  /**
   * Adjusts the specified constraint to additional connection limitations (such as products/projects for which this connection is configured).
   */
  @ThreadAWT
  Constraint adjustConstraint(@NotNull Constraint constraint);

  /**
   * Adjusts the specified items view to additional connection limitations (such as products/projects for which this connection is configured).
   * @param view readonly
   * @param life span in which there is sense in calling cont, when it's ended, no calls will be made
   * @param cont when view is adjusted, receives it
   */
  @ThreadAWT
  void adjustView(@NotNull DBFilter view, Lifespan life, @NotNull Procedure<DBFilter> cont);

  enum Capability {
    EDIT_ITEM,
    CREATE_ITEM
  }

  enum AutoSyncMode {
    AUTOMATIC("AUTOMATIC"),
    MANUAL("MANUAL");

    private final String myId;

    AutoSyncMode(String id) {
      myId = id;
    }

    public String getId() {
      return myId;
    }

    public static AutoSyncMode forId(String id) {
      for(final AutoSyncMode mode : values()) {
        if(mode.myId.equalsIgnoreCase(id)) {
          return mode;
        }
      }
      return AUTOMATIC;
    }
  }
}