package com.almworks.api.explorer.rules;

import com.almworks.api.application.ItemWrapper;
import com.almworks.api.application.qb.FilterNode;
import com.almworks.explorer.qbuilder.filter.FilterGramma;
import com.almworks.util.text.parser.ParseException;
import com.almworks.util.ui.actions.EnableState;
import org.almworks.util.Log;

/**
 * @author dyoma
 */
public class AutoAssignRule {
  private final FilterNode myCondition;
  private final String myName;
  private final String myUser;
  private final String myFormula;

  public AutoAssignRule(String name, String user, String formula) {
    myName = name;
    myUser = user;
    myFormula = formula;
    FilterNode condition;
    try {
      condition = FilterGramma.parse(myFormula);
    } catch (ParseException e) {
      Log.error("Cannot parse auto assign rule: " + e.getMessage());
      condition = null;
    }
    myCondition = condition;
  }

  public EnableState isApplicable(RulesManager rulesManager, ItemWrapper wrapper) {
    return ResolvedRule.isApplicable(wrapper, myCondition, rulesManager);
  }

  public String getUser() {
    return myUser;
  }

  public String getName() {
    return myName;
  }
}
