package com.almworks.tags;

import com.almworks.api.application.*;
import com.almworks.api.application.qb.ConstraintDescriptor;
import com.almworks.api.application.tree.TagNode;
import com.almworks.api.engine.Engine;
import com.almworks.engine.items.DatabaseUnwrapper;
import com.almworks.explorer.qbuilder.filter.*;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.dp.DPIntersects;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.LongObjFunction2;
import com.almworks.util.exec.Context;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.almworks.util.detach.Lifespan;
import org.picocontainer.Startable;

import java.util.*;

public class TagsComponentImpl implements Startable, TagsComponent {
  private final Engine myEngine;
  private final Database myDB;
  private final NameResolver myResolver;
  private final MyFactory myFactory = new MyFactory();
  private final BaseEnumConstraintDescriptor myDescriptor;
  private final ItemKeyModelCollector<?> myModelCollector;

  public TagsComponentImpl(Engine engine, NameResolver resolver, Database db) {
    myEngine = engine;
    myResolver = resolver;
    myDB = db;
    BoolExpr<DP> filter = DPEqualsIdentified.create(DBAttribute.TYPE, TYPE_TAG);
    myModelCollector = ItemKeyModelCollector.create(myFactory, filter, "Tags", resolver.getCache());
    myDescriptor =
      BaseEnumConstraintDescriptor.create(TAGS, EnumNarrower.IDENTITY, getDislayableName(), null,
        EnumConstraintKind.INTERSECTION, null, null, ItemKey.COMPARATOR, null, false, myModelCollector);
    myDescriptor.setIcon(Icons.TAG_DEFAULT);
  }

  public String getDislayableName() {
    return "Tags";
  }

  public void start() {
    myModelCollector.start(Lifespan.FOREVER, myDB);
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        myEngine.registerGlobalDescriptor(myDescriptor);
      }
    });
  }

  private static BoolExpr<DP> getTagsQuery() {
    return DPEqualsIdentified.create(DBAttribute.TYPE, TYPE_TAG);
  }

  public ConstraintDescriptor getDescriptor() {
    assert myDescriptor != null : this;
    return myDescriptor;
  }


  public void stop() {
  }

  public LongArray getAllTags(DBReader reader) {
    DBFilter tagsView = getTagsView();
    if (tagsView == null) {
      assert false;
      return null;
    }
    return tagsView.query(reader).copyItemsSorted();
  }

  private DBFilter getTagsView() {
    return myDB.filter(getTagsQuery());
  }

  public List<ResolvedTag> loadTags(long item, DBReader reader) {
    Set<Long> value = TAGS.getValue(item, reader);
    if (value == null || value.isEmpty())
      return Collections15.emptyList();
    List<ResolvedTag> result = Collections15.arrayList(value.size());
    ExplorerComponent explorer = Context.get(ExplorerComponent.class);
    Map<DBIdentifiedObject, TagNode> existingTags = explorer == null ? null : explorer.getTags();
    for (Long v : value) {
      if (existingTags != null) {
        boolean found = false;
        for (DBIdentifiedObject object : existingTags.keySet()) {
          if (Util.equals(v, reader.findMaterialized(object))) {
            found = true;
            break;
          }
        }
        if (!found) {
          continue;
        }
      }

      ResolvedTag tag = myResolver.getCache().getItemKeyOrNull(v, reader, myFactory);
      if (tag != null) {
        result.add(tag);
      }
    }
    return result;
  }

  public static void deleteTag(DBWriter writer, long tagItem) {
    deleteTag(writer, tagItem, null);
  }

  public static void deleteTag(DBWriter writer, long tagItem, BoolExpr<DP> taggedItems) {
    if (taggedItems == null) {
      taggedItems = DPIntersects.create(TAGS, Collections.singleton(tagItem));
    }
    deleteReferences(writer, tagItem, taggedItems);
    DatabaseUnwrapper.clearItem(writer, tagItem);
  }

  public static void deleteReferences(final DBWriter writer, final long tagItem, BoolExpr<DP> taggedItems) {
    writer.query(taggedItems).fold(null, new LongObjFunction2<Object>() {
      @Override
      public Object invoke(long primaryItem, Object b) {
        Set<Long> value = writer.getValue(primaryItem, TAGS);
        if (value != null) {
          value.remove(tagItem);
          writer.setValue(primaryItem, TAGS, value);
        } else assert false : primaryItem; // query is bad?
        return null;
      }
    });
  }

  private static class MyFactory implements ResolvedFactory<ResolvedTag> {
    @Override
    public ResolvedTag createResolvedItem(long item, DBReader reader) throws BadItemException {
      if (!Util.equals(DBAttribute.TYPE.getValue(item, reader), reader.findMaterialized(TYPE_TAG))) {
        throw new BadItemException("wrong type " + DBAttribute.TYPE.getValue(item, reader), item);
      }
      String name = Util.NN(DBAttribute.NAME.getValue(item, reader), "Tag " + item);
      String iconPath = ICON_PATH.getValue(item, reader);
      return new ResolvedTag(item, name, ItemOrder.byString(name), iconPath);
    }
  }
}
