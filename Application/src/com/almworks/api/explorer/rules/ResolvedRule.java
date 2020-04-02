package com.almworks.api.explorer.rules;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.NameResolver;
import com.almworks.api.application.qb.FilterNode;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.ItemHypercubeImpl;
import com.almworks.explorer.tree.KnownConstraints;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.ui.actions.EnableState;
import org.jetbrains.annotations.*;

/**
 * @author dyoma
 */
public class ResolvedRule {
  private final FilterNode myFilter;
  private final Connection myConnection;
  @Nullable
  private Constraint myConstraint = null;

  public ResolvedRule(FilterNode filter, Connection connection) {
    myFilter = filter;
    myConnection = connection;
  }

  public boolean normalize(NameResolver resolver) {
    synchronized(this) {
      if (myConstraint != null)
        return true;
      long connectionItem = myConnection.getConnectionItem();
      ItemHypercubeImpl cube = new ItemHypercubeImpl();
      cube.addValue(SyncAttributes.CONNECTION, connectionItem, true);
      myFilter.normalizeNames(resolver, cube);
      Constraint constraint = myFilter.createConstraint(cube);
      if (!KnownConstraints.isValid(constraint))
        return false;
      myConstraint = constraint;
      return myConstraint != null;
    }
  }

  public boolean isResolved() {
    synchronized (this) {
      return myConstraint != null;
    }
  }

  public boolean isApplicable(ItemWrapper wrapper) {
    if (!myConnection.equals(wrapper.getConnection()))
      return false;
    Constraint constraint;
    synchronized(this) {
      constraint = myConstraint;
    }
    if (constraint == null)
      return false;
    return wrapper.matches(constraint);
  }

  public static EnableState isApplicable(@Nullable ItemWrapper wrapper, @Nullable FilterNode condition, @Nullable RulesManager rulesManager) {
    if (condition == null || wrapper == null || rulesManager == null)
      return EnableState.INVISIBLE;
    Connection connection = wrapper.getConnection();
    if (connection == null)
      return EnableState.INVISIBLE;
    ResolvedRule state = rulesManager.getResolvedRule(connection, condition);
    if (state == null || !state.isResolved())
      return EnableState.INVISIBLE;
    return state.isApplicable(wrapper) ? EnableState.ENABLED : EnableState.DISABLED;
  }
}
