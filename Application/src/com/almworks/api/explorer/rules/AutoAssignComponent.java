package com.almworks.api.explorer.rules;

import com.almworks.util.properties.Role;

import java.util.List;

/**
 * @author dyoma
 */
public interface AutoAssignComponent {
  Role<AutoAssignComponent> ROLE = Role.role(AutoAssignComponent.class);

  List<AutoAssignRule> getRules();

  boolean hasRules();
}
