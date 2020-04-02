package com.almworks.api.explorer.gui;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.UserChanges;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.Convertor;
import com.almworks.util.commons.Condition;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
public class KeyAttributeResolver extends TextResolver {
  private final DBAttribute<String> myResolvingAttribute;
  private final DBIdentifiedObject myItemType;
  private final ResolverItemFactory myFactory;
  private final Condition<String> myTextValidator;
  private final Convertor<String, String> myKeyConvertor;

  public KeyAttributeResolver(DBAttribute<String> resolvingAttribute, DBIdentifiedObject type, ResolverItemFactory factory,
    Condition<String> textValidator, Convertor<String, String> keyConvertor)
  {
    assert textValidator != null;
    assert factory != null;
    myResolvingAttribute = resolvingAttribute;
    myItemType = type;
    myFactory = factory;
    myTextValidator = textValidator;
    myKeyConvertor = keyConvertor != null ? keyConvertor : Convertor.Identity.<String>create();
  }

  @Override
  public long resolve(String text, UserChanges changes) {
    final boolean valid = myTextValidator.isAccepted(text);
    if(!valid) {
      return 0;
    }

    final BoolExpr<DP> connExpr = DPEquals.create(SyncAttributes.CONNECTION, changes.getConnectionItem());
    final BoolExpr<DP> typeExpr = DPEqualsIdentified.create(DBAttribute.TYPE, myItemType);

    final String key = myKeyConvertor.convert(text);
    BoolExpr<DP> expr = BoolExpr.and(connExpr, typeExpr, DPEquals.create(myResolvingAttribute, key));

    final long resolution = DatabaseUnwrapper.query(changes.getCreator().getReader(), expr).getItem();
    if(resolution != 0) {
      return resolution;
    }
    return myFactory == null ? 0 : myFactory.createItem(text, changes);
  }

  public boolean isSameArtifact(ItemKey key, String text) {
    return text.trim().equalsIgnoreCase(key.getDisplayName());
  }

  public ItemKey getItemKey(@NotNull final String text) {
    return new UnresolvedItem(this, text);
  }
}