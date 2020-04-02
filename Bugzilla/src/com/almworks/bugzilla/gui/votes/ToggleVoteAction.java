package com.almworks.bugzilla.gui.votes;

import com.almworks.api.actions.*;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.UiItem;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.OptionalFieldsTracker;
import com.almworks.integers.LongList;
import com.almworks.items.sync.SyncManager;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.util.Env;
import com.almworks.util.Terms;
import com.almworks.util.collections.LongSet;
import com.almworks.util.i18n.Local;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Set;

import static com.almworks.bugzilla.provider.meta.BugzillaKeys.voteKeys;

public class ToggleVoteAction extends SimpleAction {
  private final InstantToggleSupport myInstantToggle = new InstantToggleSupport("toggleVote");

  public ToggleVoteAction() {
    super("Vote", Icons.VOTE_ACTION);
    setDefaultPresentation(PresentationKey.TOGGLED_ON, false);
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.INVISIBLE);
    if(!Env.isMac()) {
      setDefaultPresentation(PresentationKey.SHORTCUT,
        KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
    }
    updateOnChange(myInstantToggle.getModifiable());
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = ItemActionUtils.updateForEditMultipleItems(context);
    if (wrappers == null || wrappers.isEmpty()) {
      return;
    }
    checkVotingIsEnabled(wrappers, context);
    Boolean commonValue = getCommonVotedValue(wrappers);
    Boolean instantToggle = myInstantToggle.getState(context);
    if (instantToggle != null) commonValue = instantToggle;
    if (commonValue == null) {
      context.setEnabled(false);
      context.putPresentationProperty(PresentationKey.TOGGLED_ON, Boolean.FALSE);
      context.putPresentationProperty(PresentationKey.NAME, "Vote");
    } else {
      context.setEnabled(true);
      context.putPresentationProperty(PresentationKey.TOGGLED_ON, commonValue);
      context.putPresentationProperty(PresentationKey.NAME, commonValue ? "Remove Vote" : "Vote");
      context.putPresentationProperty(PresentationKey.SHORT_DESCRIPTION,
        commonValue ? "Remove votes from selected " + Terms.ref_artifacts :
          "Place one vote for selected " + Terms.ref_artifacts);
    }
  }

  static void checkVotingIsEnabled(List<ItemWrapper> wrappers, UpdateContext context) throws CantPerformException {
    boolean enabled = true;

    final Set<BugzillaConnection> connections = Collections15.hashSet();
    for(final ItemWrapper w : wrappers) {
      final BugzillaConnection c = BugzillaConnection.getInstance(w);
      if(c != null) {
        connections.add(c);
      } else {
        enabled = false;
        break;
      }
    }

    if(enabled) {
      for(final BugzillaConnection c : connections) {
        final OptionalFieldsTracker tracker = c.getContext().getOptionalFieldsTracker();
        context.updateOnChange(tracker.getModifiable());
        if(tracker.isUnused(BugzillaAttribute.TOTAL_VOTES)) {
          enabled = false;
        }
      }
    }

    if(!enabled) {
      ActionUtil.setNoAction(context);
      throw new CantPerformException();
    }
  }

  private static Boolean getCommonVotedValue(List<ItemWrapper> wrappers) {
    Boolean commonValue = null;
    for (ItemWrapper wrapper : wrappers) {
      Integer value = wrapper.getModelKeyValue(voteKeys.votesMy);
      boolean voted = value != null && value > 0;
      if (commonValue == null) {
        commonValue = voted;
      } else if (commonValue != voted) {
        commonValue = null;
        break;
      }
    }
    return commonValue;
  }

  protected void doPerform(final ActionContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = context.getSourceCollection(ItemWrapper.ITEM_WRAPPER);
    Boolean commonVoted = getCommonVotedValue(wrappers);
    if(commonVoted == null) {
      throw new CantPerformExceptionExplained(Local.parse("Some of selected " + Terms.ref_artifacts + " are voted for, some are not -- what shall I do?"));
    }

    LongList items = LongSet.collect(UiItem.GET_ITEM, wrappers);
    AggregatingEditCommit commit = new AggregatingEditCommit();
    commit.addProcedure(null, new ChangeVoteAction.CommitMyVotes(items, commonVoted ? 0 : 1, false));
    SavedToLocalDBMessage.addTo(context, commit, "toggleVoteAction");
    boolean started = context.getSourceObject(SyncManager.ROLE).commitEdit(items, commit);
    CantPerformException.ensure(started);

    myInstantToggle.setState(context, !commonVoted);
  }
}
