package com.almworks.explorer.assign;

import com.almworks.api.explorer.rules.AutoAssignComponent;
import com.almworks.api.explorer.rules.AutoAssignRule;
import com.almworks.api.misc.WorkArea;
import com.almworks.api.misc.WorkAreaUtil;
import com.almworks.util.config.ReadonlyConfiguration;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.util.Collections;
import java.util.List;

/**
 * @author dyoma
 */
public class AutoAssignComponentImpl implements Startable, AutoAssignComponent {
  private final List<AutoAssignRule> myRules = Collections15.arrayList();
  private final WorkArea myWorkArea;

  public AutoAssignComponentImpl(WorkArea workArea) {
    myWorkArea = workArea;
  }

  public void start() {
    ReadonlyConfiguration config =
      WorkAreaUtil.loadEtcConfiguration(myWorkArea, WorkArea.ETC_AUTO_ASSIGN_XML, "cannot read auto-assign file");
    loadRules(config, myRules);
  }

  public void stop() {

  }

  public List<AutoAssignRule> getRules() {
    return Collections.unmodifiableList(myRules);
  }

  public boolean hasRules() {
    return !myRules.isEmpty();
  }

  private static void loadRules(@Nullable ReadonlyConfiguration config, List<AutoAssignRule> rules) {
    if (config == null)
      return;
    for (ReadonlyConfiguration ruleConfig : config.getAllSubsets("rule"))
      try {
        rules.add(loadRule(ruleConfig));
      } catch (ReadonlyConfiguration.NoSettingException e) {
        assert false;
        Log.error(e);
      }
  }

  private static AutoAssignRule loadRule(ReadonlyConfiguration ruleConfig) throws ReadonlyConfiguration.NoSettingException {
    String name = ruleConfig.getMandatorySetting("name");
    String user = ruleConfig.getMandatorySetting("user");
    String formula = ruleConfig.getMandatorySetting("condition");
    return new AutoAssignRule(name, user, formula);
  }
}
