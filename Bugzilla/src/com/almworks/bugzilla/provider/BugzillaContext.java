package com.almworks.bugzilla.provider;

import com.almworks.api.engine.RemoteQuery;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.items.api.DBFilter;
import com.almworks.spi.provider.ConnectionContext;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.model.ArrayListCollectionModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.properties.Role;
import org.jetbrains.annotations.*;

public interface BugzillaContext extends ConnectionContext {
  Role<BugzillaContext> ROLE = Role.role(BugzillaContext.class);

  BugzillaIntegration getIntegration(BugzillaAccessPurpose purpose) throws ConnectionNotConfiguredException;

  @NotNull
  ScalarModel<OurConfiguration> getConfiguration();

  CommonMetadata getMetadata();

  PrivateMetadata getPrivateMetadata();

  DBFilter getBugsView();

  ArrayListCollectionModel<RemoteQuery> getRemoteQueries();

  boolean isCommentPrivacyAccessible();

  void setCommentPrivacyAccessible(boolean accessible, String source);

  BugzillaCustomFields getCustomFields();

  WorkflowTracker getWorkflowTracker();

  PermissionTracker getPermissionTracker();

  ComponentDefaultsTracker getComponentDefaultsTracker();

  OptionalFieldsTracker getOptionalFieldsTracker();

  <T> T getActor(Role<T> role);

  void lockIntegration(Object owner) throws InterruptedException;

  void unlockIntegration(Object owner);
}

