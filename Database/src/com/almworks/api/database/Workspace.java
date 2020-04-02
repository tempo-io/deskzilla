package com.almworks.api.database;

import com.almworks.api.database.typed.TypedArtifact;
import com.almworks.util.progress.Progress;
import com.almworks.util.properties.Role;
import org.almworks.util.TypedKey;
import org.picocontainer.Startable;

public interface Workspace extends Startable, TransactionControl {
  Role<Workspace> ROLE = Role.role(Workspace.class.getName(), Workspace.class);

  void close();

  AspectManager getAspectManager();

  FilterManager getFilterManager();

  <T extends TypedArtifact> T getSystemObject(TypedKey<T> artifactKey);

  SystemViews getViews();

  void open();

  void repair();

  long getUnderlyingUCN();

  WCN getCurrentWCN();

  Artifact getArtifactByKey(long key) throws InvalidItemKeyException;

  void dropIndexes(Progress progress) throws InterruptedException;

  IndexCheckerState getIndexCheckerState();
}
