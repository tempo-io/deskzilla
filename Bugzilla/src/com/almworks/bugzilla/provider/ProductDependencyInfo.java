package com.almworks.bugzilla.provider;

import com.almworks.api.application.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.schema.Product;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.bugzilla.provider.meta.groups.GroupsIO;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.util.AttributeMap;
import com.almworks.items.util.AttributeValueFunction;
import com.almworks.util.advmodel.FilteringListDecorator;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.commons.Condition;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.threads.*;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import java.util.*;

import static org.almworks.util.Collections15.hashMap;
import static org.almworks.util.Collections15.hashSet;

public class ProductDependencyInfo extends SimpleModifiable {
  private final Map<BugzillaAttribute, Set<ResolvedItem>> myAllowedReferents;
  private final NameResolver myNameResolver;

  private BugGroupInfo myDefaultGroups;
  private Map<DBAttribute, ResolvedItem> myDefaultValues;

  public ProductDependencyInfo(NameResolver nameResolver) {
    myNameResolver = nameResolver;
    myAllowedReferents = buildEmptyMap();
  }

  private static Map<BugzillaAttribute, Set<ResolvedItem>> buildEmptyMap() {
    Map<BugzillaAttribute, Set<ResolvedItem>> map = Collections15.hashMap();
    map.put(BugzillaAttribute.COMPONENT, Collections15.<ResolvedItem>hashSet());
    map.put(BugzillaAttribute.VERSION, Collections15.<ResolvedItem>hashSet());
    map.put(BugzillaAttribute.TARGET_MILESTONE, Collections15.<ResolvedItem>hashSet());
    return Collections.unmodifiableMap(map);
  }

  @CanBlock
  public void update(ItemVersion product) {
    updateSet(product, Product.COMPONENTS, SingleEnumAttribute.COMPONENT);
    updateSet(product, Product.VERSIONS, SingleEnumAttribute.VERSION);
    updateSet(product, Product.MILESTONES, SingleEnumAttribute.TARGET_MILESTONE);
    updateGroupInfo(product);
    updateDefaultValues(product);
    fireChanged();
  }

  private void updateDefaultValues(ItemVersion product) {
    final ItemKeyCache cache = myNameResolver.getCache();

    AttributeMap dbDefaultValues = product.getValue(Product.AT_DEFAULT_VALUES);
    Map<DBAttribute, ResolvedItem> defaultValues = hashMap();
    if (dbDefaultValues != null) {
      defaultValues = dbDefaultValues.fold(defaultValues, new AttributeValueFunction<Map<DBAttribute, ResolvedItem>>() {
        @Override
        public <T> Map<DBAttribute, ResolvedItem> f(DBAttribute<T> attribute, T value, Map<DBAttribute, ResolvedItem> building) {
          if (attribute.getValueClass() == Long.class) {
            ResolvedItem reference = cache.findExisting((Long)value);
            if (reference != null) {
              building.put(attribute, reference);
            }
          }
          return building;
        }
      });
      synchronized (myAllowedReferents) {
        myDefaultValues = Collections.unmodifiableMap(defaultValues);
      }
    }
  }

  private void updateGroupInfo(ItemVersion product) {
    BugGroupInfo info = GroupsIO.buildGroupInfo(product);
    synchronized(myAllowedReferents) {
      myDefaultGroups = info;
    }
  }

  @CanBlock
  private void updateSet(ItemVersion product, DBAttribute<List<Long>> attribute, SingleEnumAttribute enumAttr) {
    ItemKeyCache cache = myNameResolver.getCache();
    List<Long> components = product.getValue(attribute);
    synchronized (myAllowedReferents) {
      Set<ResolvedItem> set = myAllowedReferents.get(enumAttr.getBugzillaAttribute());
      set.clear();
      if (components != null) {
        for (long component : components) {
          ResolvedItem key = cache.getItemKeyOrNull(component, product.getReader(), enumAttr.getFactory());
          if (key != null) {
            set.add(key);
          }
        }
      }
    }
  }

  public ItemKey selectAnyValid(List<ItemKey> variants, BugzillaAttribute attribute) {
    synchronized (myAllowedReferents) {
      Set<? extends ItemKey> resolved = myAllowedReferents.get(attribute);
      for (ItemKey variant : variants) {
        if (resolved.contains(variant))
          return variant;
      }
    }
    return null;
  }

  /**
   * Returns a copy of a set of valid values for the specified attribute.
   * If attribute is unknown, returns empty set.
   * @param attribute
   * @return
   */
  public Set<ResolvedItem> getValidValues(BugzillaAttribute attribute) {
    synchronized (myAllowedReferents) {
      return hashSet(myAllowedReferents.get(attribute));
    }
  }

  @ThreadAWT
  public void filterVariants(final FilteringListDecorator<ItemKey> variants, final BugzillaAttribute attribute,
    Lifecycle filterLife)
  {
    Threads.assertAWTThread();
    filterLife.cycle();
    final Set<? extends ItemKey> set = myAllowedReferents.get(attribute);

    ChangeListener listener = new ChangeListener() {
      public void onChange() {
        synchronized (getLock()) {
          boolean empty;
          synchronized (myAllowedReferents) {
            empty = set == null || set.size() == 0;
          }
          if (empty) {
            // no referents specified, show none
            variants.setFilter(Condition.<ItemKey>never());
          } else {
            // for optimization, remove all elements from model in one event
            variants.setFilter(Condition.<ItemKey>never());
            variants.setFilter(new Condition<ItemKey>() {
              public boolean isAccepted(ItemKey key) {
                synchronized (myAllowedReferents) {
                  return set.contains(key);
                }
              }
            });
          }
        }
      }
    };

    listener.onChange();
    addChangeListener(filterLife.lifespan(), ThreadGate.AWT, listener);
  }

  public void removed() {
    fireChanged();
  }

  public boolean contains(BugzillaAttribute attribute, long value) {
    synchronized (myAllowedReferents) {
      Set<ResolvedItem> allowed = myAllowedReferents.get(attribute);
      for (ResolvedItem resolvedArtifact : allowed) {
        if (value == resolvedArtifact.getResolvedItem())
          return true;
      }
      return false;
    }
  }

  public boolean containsAny(BugzillaAttribute attribute, Collection<Long> values) {
    synchronized (myAllowedReferents) {
      Set<ResolvedItem> allowed = myAllowedReferents.get(attribute);
      for (ResolvedItem resolvedArtifact : allowed) {
        if (values.contains(resolvedArtifact.getResolvedItem()))
          return true;
      }
      return false;
    }
  }

  public BugGroupInfo getDefaultGroups() {
    synchronized (myAllowedReferents) {
      return myDefaultGroups;
    }
  }

  @Nullable
  public Map<DBAttribute, ResolvedItem> getDefaultValues() {
    synchronized (myAllowedReferents) {
      return myDefaultValues;
    }
  }
}
