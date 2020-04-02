package com.almworks.api.actions;

import com.almworks.api.application.*;
import com.almworks.util.Terms;
import com.almworks.util.commons.Function;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

import java.util.List;

/**
 * @author Alex
 */
public class VoteForIssueAction extends ChangeBooleanUserListStateAction {
  private final ModelKey<Boolean> myCanVote;

  public VoteForIssueAction(ModelKey<Boolean> canVote, ModelKey<Boolean> meVoted, ModelKey<List<ItemKey>> voteList,
    ModelKey<Integer> votes, Function<ItemWrapper, ItemKey> meGetter)
  {
    super("Vote", Icons.VOTE_ACTION, meVoted, votes, voteList, meGetter, "changeMyVote");
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Change your vote for selected " + Terms.ref_artifact);
    myCanVote = canVote;
  }

  protected void customUpdate(UpdateContext context) throws CantPerformException {
    List<ItemWrapper> wrappers = ItemActionUtils.updateForEditMultipleItems(context);
    if (wrappers == null || wrappers.isEmpty()) {
      return;
    }
    Boolean canVote = ItemActionUtils.getSameForAllKeyValue(myCanVote, wrappers);
    if (canVote == null || !canVote) {
      context.putPresentationProperty(PresentationKey.NAME, "Can't vote");
      context.setEnabled(false);
      return;
    }
    updateNameAndToggled(context, wrappers, "Vote", "Remove Vote",
      "Vote state is different for selected $(app.term.artifacts)");
    IdActionProxy.setShortcut(context, MetaInfo.VOTE_FOR_ARTIFACT);
  }
}
