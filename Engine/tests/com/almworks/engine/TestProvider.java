package com.almworks.engine;

import com.almworks.api.engine.*;
import com.almworks.container.TestContainer;
import com.almworks.integers.LongList;
import com.almworks.items.api.DP;
import com.almworks.items.api.Database;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.model.ScalarModel;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

public class TestProvider implements ItemProvider, Startable {
  private final TestCommonMeta myMeta = new TestCommonMeta();
  private final Database myDatabase;

  public TestProvider(Database database) {
    myDatabase = database;
  }

  public void start() {
  }

  public void stop() {
  }

  public Connection createConnection(String connectionID, ReadonlyConfiguration configuration, boolean isNew) {
    return new TestConnection(myDatabase, myMeta, this, connectionID, new TestContainer());
  }

  public void showNewConnectionWizard() {}

  public void showEditConnectionWizard(Connection connection) {}

  @Override
  public boolean isEditingConnection(Connection connection) {
    return false;
  }

  public String getProviderID() {
    return "test";
  }

  public String getProviderName() {
    return "TestProvider";
  }

  public ScalarModel<ItemProviderState> getState() {
    throw new UnsupportedOperationException();
  }

  public Configuration createDefaultConfiguration(String itemUrl) {
    return null;
  }

  public boolean isItemUrl(String url) {
    return false;
  }

  @Override
  public String getDisplayableItemIdFromUrl(String url) {
    return null;
  }

  public boolean isEnabled() {
    return true;
  }

  public ProviderActivationAgent createActivationAgent() {
    return null;
  }

  public Configuration getConnectionConfig(String connectionID) {
    return null;
  }

  @Override
  public PrimaryItemStructure getPrimaryStructure() {
    return new PrimaryItemStructure() {
      @NotNull
      @Override
      public BoolExpr<DP> getPrimaryItemsFilter() {
        return BoolExpr.TRUE();
      }

      @NotNull
      @Override
      public BoolExpr<DP> getLocallyChangedFilter() {
        return BoolExpr.TRUE();
      }

      @NotNull
      @Override
      public BoolExpr<DP> getConflictingItemsFilter() {
        return BoolExpr.TRUE();
      }

      @NotNull
      @Override
      public BoolExpr<DP> getUploadableItemsFilter() {
        return BoolExpr.TRUE();
      }

      @NotNull
      @Override
      public LongList loadEditableSlaves(ItemVersion primary) {
        return LongList.EMPTY;
      }
    };
  }
}
