package com.almworks.api.application.viewer.textdecorator;

import com.almworks.util.properties.Role;

import java.util.Collection;

public interface TextDecoratorRegistry {
  Role<TextDecoratorRegistry> ROLE = Role.role(TextDecoratorRegistry.class);

  void addParser(TextDecorationParser parser);

  Collection<? extends TextDecoration> processText(String text);
}
