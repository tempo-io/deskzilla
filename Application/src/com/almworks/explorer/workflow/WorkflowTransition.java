package com.almworks.explorer.workflow;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.MetaInfo;
import com.almworks.api.application.qb.FilterNode;
import com.almworks.api.explorer.rules.ResolvedRule;
import com.almworks.api.explorer.rules.RulesManager;
import com.almworks.util.text.NameMnemonic;
import com.almworks.util.ui.actions.EnableState;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;

/**
 * @author dyoma
 */
class WorkflowTransition {
  private final NameMnemonic myName;
  @Nullable private final Icon myIcon;
  private final FilterNode myStateKey;
  private final List<LoadedEditPrimitive> myEditScript;
  private final String myWindowId;
  private final String myIdentity;

  public WorkflowTransition(
    NameMnemonic name, List<LoadedEditPrimitive> editScript, Icon icon,
    String windowId, FilterNode filterNode)
  {
    myName = name;
    myIcon = icon;
    myWindowId = windowId;
    myEditScript = editScript;
    myStateKey = filterNode;

    if (myWindowId != null && myWindowId.length() > 0) {
      myIdentity = (name.getText() + ":" + myWindowId).trim();
    } else {
      myIdentity = name.getText().trim();
    }
  }

  public NameMnemonic getName() {
    return myName;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public EnableState isApplicable(RulesManager rulesManager, MetaInfo metaInfo, List<ItemWrapper> items) {
    for(final ItemWrapper w : items) {
      final EnableState e = ResolvedRule.isApplicable(w, myStateKey, rulesManager);
      if(e != EnableState.ENABLED) {
        return e;
      }
    }
    for(final LoadedEditPrimitive p : myEditScript) {
      if(!p.isApplicable(metaInfo, items)) {
        return EnableState.INVISIBLE;
      }
    }
    return EnableState.ENABLED;
  }

  public String getWindowId() {
    return myWindowId;
  }

  public List<LoadedEditPrimitive> getEditScript() {
    return myEditScript;
  }

  public String getIdentity() {
    return myIdentity;
  }
}
