package com.almworks.bugzilla.provider.qb;

import com.almworks.api.application.qb.FilterNode;
import com.almworks.bugzilla.provider.qb.flags.FlagConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.CustomParserProvider;
import com.almworks.util.text.parser.TokenRegistry;

public class BugzillaCustomParserProvider implements CustomParserProvider {
  @Override
  public void registerParsers(TokenRegistry<FilterNode> registry) {
    FlagConstraintDescriptor.registerParsers(registry);
  }
}
