package com.almworks.api.explorer.rules;

import com.almworks.api.application.*;
import com.almworks.api.engine.Connection;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.Trio;
import com.almworks.util.commons.Function;
import com.almworks.util.ui.actions.*;
import org.almworks.util.*;

import java.util.*;

/**
 * @author dyoma
 */
public abstract class BaseAutoAssignAction implements AnAction {
  private final Map<PresentationKey, Object> myPresentation;
  private final PresentationKey<String> myHintKey;
  private final Function<ItemWrapper, Edit> myGetEdit;

  protected BaseAutoAssignAction(Map<PresentationKey, Object> presentation, PresentationKey<String> hintKey, Function<ItemWrapper, Edit> getEdit) {
    myPresentation = presentation != null ? presentation : Collections15.<PresentationKey, Object>emptyMap();
    myHintKey = hintKey;
    myGetEdit = getEdit;
  }

  protected <T extends ItemWrapper> ApplicableRules<T> basicUpdate(UpdateContext context, TypedKey<? extends T> keyItem)
    throws CantPerformException
  {
    context.setEnabled(EnableState.INVISIBLE);
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    if (!context.getSourceObject(AutoAssignComponent.ROLE).hasRules())
      throw new CantPerformException();
    ApplicableRules<T> applicable = collectApplicable(context, keyItem);
    context.setEnabled(EnableState.DISABLED);
    for (Map.Entry<PresentationKey, Object> entry : myPresentation.entrySet())
      context.putPresentationProperty(entry.getKey(), entry.getValue());
    if (applicable.getCount() == 0)
      throw new CantPerformException();
    return applicable;
  }

  protected <T extends ItemWrapper> ApplicableRules<T> collectApplicable(ActionContext context, TypedKey<? extends T> keyItem)
    throws CantPerformException
  {
    List<T> items = context.getSourceCollection(keyItem);
    CantPerformException.ensure(!items.isEmpty());
    ApplicableRules<T> applicable = ApplicableRules.create(context);
    applicable.findApplicableRules(items, myGetEdit);
    return applicable;
  }

  protected void finishUpdate(UpdateContext context, ApplicableRules rules) {
    context.setEnabled(EnableState.ENABLED);
    if (myHintKey != null) {
      String user = rules.getSingleUser();
      if (user != null)
        context.putPresentationProperty(myHintKey, "Auto Assign to " + user);
      else
        context.putPresentationProperty(myHintKey, "Auto Assign");
    }
  }

  protected static class ApplicableRules<T extends ItemWrapper> {
    private final List<Trio<T, AutoAssignRule, Edit>> myApplicableRules = Collections15.arrayList();
    private final RulesManager myRulesManager;
    private final List<AutoAssignRule> myRules;

    private ApplicableRules(RulesManager rulesManager, List<AutoAssignRule> rules) {
      myRulesManager = rulesManager;
      myRules = rules;
    }

    public static AutoAssignAction.ApplicableRules create(ActionContext context) throws CantPerformException {
      RulesManager rulesManager = context.getSourceObject(RulesManager.ROLE);
      AutoAssignComponent assignComponent = context.getSourceObject(AutoAssignComponent.ROLE);
      return new AutoAssignAction.ApplicableRules(rulesManager, assignComponent.getRules());
    }

    public boolean findApplicableRules(Collection<? extends T> items, Function<ItemWrapper, Edit> getAssign) throws CantPerformException {
      if (items.isEmpty())
        return false;
      for (T item : items) {
        checkItem(item);
        Edit assign = getAssign.invoke(item);
        AutoAssignRule rule = assign != null ? findApplicableRule(item) : null;
        if (rule != null) {
          assert assign != null;
          myApplicableRules.add(Trio.create(item, rule, assign));
        }
      }
      return myApplicableRules.size() == items.size();
    }

    private void checkItem(T item) throws CantPerformException {
      CantPerformException.ensure(CantPerformException.ensureNotNull(item.getConnection()).hasCapability(Connection.Capability.EDIT_ITEM));
    }

    private AutoAssignRule findApplicableRule(ItemWrapper item) {
      for (AutoAssignRule rule : myRules)
        if (rule.isApplicable(myRulesManager, item) == EnableState.ENABLED)
          return rule;
      return null;
    }

    public int getCount() {
      return myApplicableRules.size();
    }

    public String getSingleUser() {
      String user = null;
      for (Trio<T, AutoAssignRule, Edit> rule : myApplicableRules) {
        String nextUser = rule.getSecond().getUser();
        if (user == null)
          user = nextUser;
        else if (user.equals(nextUser))
          continue;
        else
          return null;
      }
      return user;
    }

    public Collection<T> getItemsCollection() {
      List<T> result = Collections15.arrayList(myApplicableRules.size());
      for (Trio<T, AutoAssignRule, Edit> trio : myApplicableRules)
        result.add(trio.getFirst());
      return result;
    }

    public T getItem(int index) {
      return myApplicableRules.get(index).getFirst();
    }

    public void applyEditOnForm(int index, ItemUiModel model) {
      Trio<T, AutoAssignRule, Edit> trio = myApplicableRules.get(index);
      AutoAssignRule rule = trio.getSecond();
      Log.debug("Applying rule " + rule.getName());
      trio.getThird().editOnForm(model, rule.getUser());
    }

    public void applyEditDb(int index, ItemVersionCreator creator) {
      Trio<T, AutoAssignRule, Edit> trio = myApplicableRules.get(index);
      AutoAssignRule rule = trio.getSecond();
      Log.debug("Applying rule " + rule.getName());
      trio.getThird().editDb(creator, rule.getUser());
    }
  }

  public static interface Edit {
    void editOnForm(ItemUiModel formModel, String user);

    void editDb(ItemVersionCreator item, String userId);
  }
}
