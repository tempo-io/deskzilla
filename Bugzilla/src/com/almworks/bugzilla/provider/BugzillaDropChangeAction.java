package com.almworks.bugzilla.provider;

import com.almworks.api.actions.ItemActionUtils;
import com.almworks.api.application.*;
import com.almworks.api.dynaforms.EditPrimitive;
import com.almworks.api.engine.Connection;
import com.almworks.api.syncreg.*;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.SingleEnumAttribute;
import com.almworks.bugzilla.provider.meta.BugzillaKeys;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.explorer.qbuilder.filter.EnumConstraintKind;
import com.almworks.explorer.workflow.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Terms;
import com.almworks.util.i18n.Local;
import com.almworks.util.model.LightScalarModel;
import com.almworks.util.ui.actions.CantPerformExceptionExplained;
import org.almworks.util.Collections15;

import java.util.*;

public class BugzillaDropChangeAction extends AbstractDropChangeAction {
  public BugzillaDropChangeAction(BugzillaConnection connection, String frameId, ItemHypercube target,
    boolean move)
  {
    super(connection, frameId, target, move);
  }

  protected List<? extends EditPrimitive> getActionFields(ItemHypercube cleared, List<ItemWrapper> items) {
    if (items.isEmpty())
      return null;
    ItemWrapper first = items.get(0);
    MetaInfo metaInfo = first.getMetaInfo();
    Connection connection = first.getConnection();
    if (metaInfo == null || connection == null) {
      assert false;
      return null;
    }
    List<EditPrimitive> fields = Collections15.arrayList();
    Set<Long> products = Collections15.linkedHashSet();
    DBAttribute<Long> productAttribute = Bug.attrProduct;
    LightScalarModel<ItemHypercube> sourceCube = buildSourceCube(items, connection, products, productAttribute);

    for (DBAttribute<?> axis : cleared.getAxes()) {
      SortedSet<Long> included = cleared.getIncludedValues(axis);
      SortedSet<Long> excluded = cleared.getExcludedValues(axis);

      if (included != null && excluded != null) {
        included = new TreeSet<Long>(included);
        included.removeAll(excluded);
      }

      ModelKey<?> modelKey = metaInfo.findKeyByAttribute(axis, connection);
      BaseEnumConstraintDescriptor descriptor = getDescriptor(axis);
      if (modelKey == null || descriptor == null) {
        assert false : axis + " " + modelKey + " " + descriptor;
        continue;
      }

      if (included != null && included.size() == 1) {
        Long ptr = included.iterator().next();
        ResolvedItem option = descriptor.findForItem(ptr);
        if (option == null) {
          assert false : included;
          continue;
        }
        boolean clear = isClearAction(included, descriptor);
        fields.add(descriptor.getKind().createReadonlyPrimitive(
          modelKey, option, descriptor.getAttribute(), isMove(), clear));
      } else {
        List<ItemKey> includedArtifacts = resolve(descriptor, included);
        List<ItemKey> excludedArtifacts = resolve(descriptor, excluded);
        if (descriptor.getKind() instanceof EnumConstraintKind.InclusionEnumKind) {
          fields.add(new ComboBoxField((ModelKey<ItemKey>) modelKey, descriptor.getMissingItem(), descriptor,
            descriptor.getAttribute(), sourceCube, null, false, null, includedArtifacts, excludedArtifacts));
        } else {
          fields.add(new SubsetListField((ModelKey<List<ItemKey>>) modelKey, descriptor, descriptor.getAttribute(), BugzillaKeys.product, productAttribute, sourceCube, products, null, null,
            includedArtifacts, excludedArtifacts));
        }
      }
    }
    return fields;
  }

  private static LightScalarModel<ItemHypercube> buildSourceCube(List<ItemWrapper> items, Connection connection, Set<Long> products, DBAttribute<Long> productAttribute) {
    final ItemHypercubeImpl cube = new ItemHypercubeImpl();
    ItemHypercubeUtils.adjustForConnection(cube, connection);
    for (ItemWrapper item : items) {
      ItemKey product = item.getModelKeyValue(BugzillaKeys.product);
      if (product != null) {
        long productArtifact = product.getResolvedItem();
        if (productArtifact > 0 && products.add(productArtifact)) {
          cube.addValue(productAttribute, productArtifact, true);
        }
      }
    }
    LightScalarModel<ItemHypercube> sourceCube = LightScalarModel.create();
    sourceCube.setValue(cube);
    return sourceCube;
  }

  private static List<ItemKey> resolve(BaseEnumConstraintDescriptor descriptor, SortedSet<Long> pointers) {
    if (pointers == null)
      return null;
    List<ItemKey> r = Collections15.arrayList();
    for (Long pointer : pointers) {
      ResolvedItem ra = descriptor.findForItem(pointer);
      if (ra != null)
        r.add(ra);
    }
    return r;
  }

  protected ItemHypercube clearUnchangeableFields(ItemHypercube target, List<ItemWrapper> items)
    throws CantPerformExceptionExplained
  {
    checkChangingFlagAttributes(target);
    target = clearCube(target, items, BugzillaKeys.reporter, Bug.attrReporter, true);
    target = clearCube(target, items, BugzillaKeys.blocks, Bug.attrBlocks, true);
    target = clearCube(target, items, BugzillaKeys.depends, Bug.attrBlockedBy, true);
    target = clearCube(target, items, BugzillaKeys.product, Bug.attrProduct, true);
    // todo voters
//    target = clearCube(target, items, mks.voteKeys.myVoterList, true);
    return target;
  }

  private static void checkChangingFlagAttributes(ItemHypercube target) throws CantPerformExceptionExplained {
    for (DBAttribute<?> axis : target.getAxes()) {
      String cachingAttribute = Flags.checkCachingAttribute(axis);
      if (cachingAttribute != null) {
        throw new CantPerformExceptionExplained("cannot change " + cachingAttribute);
      }
    }
  }

  protected ItemHypercube clearDependentFieldValues(ItemHypercube target, List<ItemWrapper> items)
    throws CantPerformExceptionExplained
  {
    target = clearDependencies(target, items, SingleEnumAttribute.COMPONENT);
    target = clearDependencies(target, items, SingleEnumAttribute.VERSION);
    target = clearDependencies(target, items, SingleEnumAttribute.TARGET_MILESTONE);
    return target;
  }

  private ItemHypercube clearDependencies(ItemHypercube target, List<ItemWrapper> items,
    SingleEnumAttribute enumAttr) throws CantPerformExceptionExplained
  {
    ProductDependenciesTracker deptracker = ((BugzillaConnection) myConnection).getDependenciesTracker();
    SortedSet<Long> included = target.getIncludedValues(enumAttr.getBugAttribute());
    if (included == null)
      return target;
    ItemKey product = ItemActionUtils.getSameForAllKeyValue(BugzillaKeys.product, items);
    if (product == null)
      throw new CantPerformExceptionExplained(Local.parse(Terms.ref_Artifacts) + " belong to different products");
    long productPtr = product.getResolvedItem();
    if (productPtr <= 0)
      return target;
    ProductDependencyInfo depinfo = deptracker.getInfo(product);
    if (depinfo == null)
      return target;
    SortedSet<Long> includedModified = null;
    BugzillaAttribute bzAttribute = enumAttr.getBugzillaAttribute();
    for (Long ap : included) {
      if (!depinfo.contains(bzAttribute, ap)) {
        if (includedModified == null) {
          includedModified = new TreeSet<Long>(included);
        }
        includedModified.remove(ap);
      }
    }
    if (includedModified == null)
      return target;
    if (includedModified.isEmpty())
      throw new CantPerformExceptionExplained(
        BugzillaUtil.getDisplayableFieldName(bzAttribute) + " invalid for product " + product.getDisplayName());
    return replaceAxis(target, enumAttr.getBugAttribute(), includedModified);
  }

  @Override
  protected BaseEnumConstraintDescriptor getStaticDescriptor(DBAttribute<?> axis) {
    return CommonMetadata.getEnumDescriptor(axis);
  }
}
