package com.almworks.bugzilla.provider.qb.flags;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.NameResolver;
import com.almworks.api.application.qb.*;
import com.almworks.api.application.tree.UserQueryNode;
import com.almworks.api.constraint.*;
import com.almworks.api.explorer.gui.TextResolver;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.engine.items.ItemStorageAdaptor;
import com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.*;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderable;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.parser.FormulaWriter;
import com.almworks.util.text.parser.TokenRegistry;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

import static com.almworks.bugzilla.provider.qb.flags.FlagConstraintEditor.REQUESTEE_NOBODY;
import static com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor.SUBSET;
import static com.almworks.explorer.qbuilder.filter.BaseEnumConstraintDescriptor.SubsetKey;

public class FlagConstraintDescriptor extends AbstractConstraintDescriptor implements ConstraintType, CanvasRenderable {
  public static final Icon FLAGS_ICON = Icons.FLAG;

  public static final String FLAGS_TOKEN = "flags";
  public static final String TYPES_TOKEN = Flags.CACHE_TYPES.getName();
  public static final String STATUSES_TOKEN = "flagStatuses";
  public static final String SETTERS_TOKEN = Flags.CACHE_SETTERS.getName();
  public static final String REQUESTEES_TOKEN = Flags.CACHE_REQUESTEES.getName();

  public static final TypedKey<List<ItemKey>> FLAG_TYPES_KEY = TypedKey.create(TYPES_TOKEN);
  public static final TypedKey<List<ItemKey>> STATUSES_KEY = TypedKey.create(STATUSES_TOKEN);
  public static final TypedKey<List<ItemKey>> SETTERS_KEY = TypedKey.create(SETTERS_TOKEN);
  public static final TypedKey<List<ItemKey>> REQUESTEES_KEY = TypedKey.create(REQUESTEES_TOKEN);

  private final SimpleModifiable myModifiable = new SimpleModifiable();
  private final EditorKeys myEditorKeys = new EditorKeys();

  private final BaseEnumConstraintDescriptor myFlagTypeDescr;
  private final BaseEnumConstraintDescriptor mySetterDescr;
  private final BaseEnumConstraintDescriptor myRequesteeDescr;
  private final TextResolver myUserResolver;

  public FlagConstraintDescriptor(
    BaseEnumConstraintDescriptor flagTypeDescr,
    BaseEnumConstraintDescriptor setterDescr,
    BaseEnumConstraintDescriptor requesteeDescr,
    TextResolver userResolver)
  {
    myFlagTypeDescr = flagTypeDescr;
    mySetterDescr = setterDescr;
    myRequesteeDescr = requesteeDescr;
    myUserResolver = userResolver;
  }

  @Override
  public String getDisplayName() {
    return "Flags";
  }

  @NotNull
  @Override
  public String getId() {
    return FLAGS_TOKEN;
  }

  @Override
  public ConstraintEditor createEditor(ConstraintEditorNodeImpl node) {
    return new FlagConstraintEditor(
      node, myEditorKeys, myFlagTypeDescr, mySetterDescr, myRequesteeDescr, myUserResolver);
  }

  @Override
  public void writeFormula(FormulaWriter writer, PropertyMap data) {
    writeFormula(writer, getId(), data);
  }

  @Override
  public ConstraintType getType() {
    return this;
  }

  @NotNull
  @Override
  public ConstraintDescriptor resolve(
    NameResolver resolver, @Nullable ItemHypercube cube, @Nullable PropertyMap data)
  {
    if(data != null) {
      doResolve(myFlagTypeDescr, FLAG_TYPES_KEY, resolver, cube, data);
      doResolve(mySetterDescr, SETTERS_KEY, resolver, cube, data);
      doResolve(myRequesteeDescr, REQUESTEES_KEY, resolver, cube, data);
      resolveSpecialStub(data, REQUESTEES_KEY, REQUESTEE_NOBODY); // must come after normal resolutions
    }
    return this;
  }

  private void doResolve(
    BaseEnumConstraintDescriptor descr, TypedKey<List<ItemKey>> realKey,
    NameResolver resolver, @Nullable ItemHypercube cube, @NotNull PropertyMap data)
  {
    putFakeSubset(data, realKey);
    final ConstraintDescriptor resolved = descr.resolve(resolver, cube, data);
    assert resolved == descr;
    restoreSubset(data, realKey);
  }

  private void putFakeSubset(PropertyMap map, TypedKey<List<ItemKey>> realKey) {
    map.put(SUBSET, map.remove(realKey));
  }

  private void restoreSubset(PropertyMap map, TypedKey<List<ItemKey>> realKey) {
    map.put(realKey, map.remove(SUBSET));
  }

  private void resolveSpecialStub(PropertyMap data, TypedKey<List<ItemKey>> key, ItemKey target) {
    final List<ItemKey> aks = data.get(key);
    if(aks == null || aks.isEmpty()) {
      return;
    }

    final int index = findCandidateStub(aks, target);
    if(index >= 0) {
      final List<ItemKey> replacement = createReplacementList(aks, index, target);
      data.replace(key, replacement);
    }
  }

  private int findCandidateStub(List<ItemKey> aks, ItemKey target) {
    int index = 0;
    for(final ItemKey ak : aks) {
      if(matches(ak, target)) {
        return index;
      }
      index++;
    }
    return -1;
  }

  private boolean matches(ItemKey ak, ItemKey target) {
    if(ak.getResolvedItem() > 0) {
      return false;
    }
    if(ak instanceof FlagConstraintEditor.Special) {
      return false;
    }
    return ak.equals(target);
  }

  private List<ItemKey> createReplacementList(List<ItemKey> aks, int index, ItemKey replacement) {
    final List<ItemKey> newList = Collections15.arrayList(aks);
    newList.set(index, replacement);
    return newList;
  }

  @Override
  public RemoveableModifiable getModifiable() {
    return myModifiable;
  }

  @Override
  public BoolExpr<DP> createFilter(PropertyMap data, ItemHypercube hypercube) {
    return ItemStorageAdaptor.dpReferredBy(Flags.AT_FLAG_MASTER, createFlagFilter(data, hypercube));
  }

  private BoolExpr<DP> createFlagFilter(PropertyMap data, ItemHypercube cube) {
    final List<BoolExpr<DP>> ands = Collections15.arrayList();
    addBasicFilters(ands);
    addFlagTypeFilter(data, cube, ands);
    addSetterFilter(data, cube, ands);
    addStatusAndRequesteeFilters(data, cube, ands);
    return BoolExpr.and(ands);
  }

  private void addBasicFilters(List<BoolExpr<DP>> list) {
    list.add(DPEqualsIdentified.create(DBAttribute.TYPE, Flags.KIND_FLAG));
    list.add(DPNotNull.create(SyncAttributes.INVISIBLE).negate());
  }

  private void addFlagTypeFilter(PropertyMap data, ItemHypercube cube, List<BoolExpr<DP>> list) {
    addSubfilter(data, Flags.AT_FLAG_TYPE, FLAG_TYPES_KEY, myFlagTypeDescr, cube, list);
  }

  private void addSetterFilter(PropertyMap data, ItemHypercube cube, List<BoolExpr<DP>> list) {
    addSubfilter(data, Flags.AT_FLAG_SETTER, SETTERS_KEY, mySetterDescr, cube, list);
  }

  private void addSubfilter(
    PropertyMap data, DBAttribute<Long> attr, TypedKey<List<ItemKey>> key,
    BaseEnumConstraintDescriptor descr, ItemHypercube cube, List<BoolExpr<DP>> list)
  {
    final List<ItemKey> keys = data.get(key);
    if(keys != null && !keys.isEmpty()) {
      final Collection<Long> items = descr.resolve(keys, cube);
      if(!items.isEmpty()) {
        list.add(DPEquals.equalOneOf(attr, items));
      } else list.add(BoolExpr.<DP>FALSE());
    }
  }

  private void addStatusAndRequesteeFilters(PropertyMap data, ItemHypercube cube, List<BoolExpr<DP>> list) {
    final Collection<Character> chars = getStatusChars(data);

    if(!hasSomething(data, REQUESTEES_KEY)) {
      if(!chars.isEmpty()) {
        list.add(DPEquals.equalOneOf(Flags.AT_FLAG_STATUS, chars));
      }
      return;
    }

    assert chars.isEmpty() || chars.contains('?') : chars;

    final BoolExpr<DP> question = createQuestionExpr(data, cube);
    final BoolExpr<DP> plusMinus = createPlusMinusExpr(chars);
    if(question != null && plusMinus != null) {
      list.add(BoolExpr.or(question, plusMinus));
    } else if(question != null) {
      list.add(question);
    } else if(plusMinus != null) {
      list.add(plusMinus);
    }
  }

  @NotNull
  private Collection<Character> getStatusChars(PropertyMap data) {
    final Collection<Character> chars = Collections15.hashSet();
    final List<ItemKey> keys = data.get(STATUSES_KEY);
    if(keys != null && !keys.isEmpty()) {
      for(final ItemKey key : keys) {
        final String dn = key.getDisplayName();
        assert dn.length() == 1 && FlagConstraintDescriptor.isLegalStatusChar(m2d(dn.charAt(0))) : dn;
        chars.add(m2d(dn.charAt(0)));
      }
    }
    return chars;
  }

  private BoolExpr<DP> createQuestionExpr(PropertyMap data, ItemHypercube cube) {
    BoolExpr<DP> reqItems = null;
    final List<ItemKey> keys = data.get(REQUESTEES_KEY);
    if(keys != null) {
      final Collection<Long> items = myRequesteeDescr.resolve(keys, cube);
      if(!items.isEmpty()) {
        reqItems = DPEquals.equalOneOf(Flags.AT_FLAG_REQUESTEE, items);
      }
    }

    BoolExpr<DP> reqEmpty = null;
    if(hasNobody(data)) {
      reqEmpty = DPNotNull.create(Flags.AT_FLAG_REQUESTEE).negate();
//      reqEmpty = DPEquals.create(Flags.AT_FLAG_REQUESTEE, -1L);
//      reqEmpty = BoolExpr.or(
//        DPEquals.create(Flags.AT_FLAG_REQUESTEE, -1L),
//        DPNotNull.create(Flags.AT_FLAG_REQUESTEE).negate());
    }

    if(reqItems != null && reqEmpty != null) {
      return questionExpr().and(reqItems.or(reqEmpty));
    } else if(reqItems != null) {
      return questionExpr().and(reqItems);
    } else if(reqEmpty != null) {
      return questionExpr().and(reqEmpty);
    } else {
      return questionExpr();
    }
  }

  private boolean hasNobody(PropertyMap data) {
    if(hasSomething(data, REQUESTEES_KEY)) {
      for(final ItemKey ik : data.get(REQUESTEES_KEY)) {
        if(ik == FlagConstraintEditor.REQUESTEE_NOBODY) {
          return true;
        }
      }
    }
    return false;
  }

  private BoolExpr<DP> questionExpr() {
    return DPEquals.create(Flags.AT_FLAG_STATUS, '?');
  }

  private BoolExpr<DP> createPlusMinusExpr(Collection<Character> chars) {
    if(chars.isEmpty()) {
      return questionExpr().negate();
    }

    if(chars.contains('+') || chars.contains('-')) {
      chars.remove('?');
      return DPEquals.equalOneOf(Flags.AT_FLAG_STATUS, chars);
    }

    return null;
  }

  private boolean hasSpecials(PropertyMap data, TypedKey<List<ItemKey>> key) {
    if(hasSomething(data, key)) {
      //noinspection ConstantConditions
      for(final ItemKey ak : data.get(key)) {
        if(isSpecial(ak)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isSpecial(ItemKey ak) {
    return ak instanceof FlagConstraintEditor.Special;
  }

  @Override
  public Constraint createConstraint(PropertyMap data, ItemHypercube cube) {
    return composeSubconstraints(createSubconstraints(cube, data));
  }

  private List<Constraint> createSubconstraints(ItemHypercube cube, PropertyMap data) {
    final List<Constraint> list = Collections15.arrayList();
    addFlagTypeSubconstraint(cube, data, list);
    addSetterSubconstraint(cube, data, list);
    addRequesteeSubconstraint(cube, data, list);
    addBlackBoxConstraint(data, list); // must come last
    return list;
  }

  private void addFlagTypeSubconstraint(ItemHypercube cube, PropertyMap data, List<Constraint> list) {
    addSubconstraint(myFlagTypeDescr, FLAG_TYPES_KEY, cube, data, list);
  }

  private void addSetterSubconstraint(ItemHypercube cube, PropertyMap data, List<Constraint> list) {
    addSubconstraint(mySetterDescr, SETTERS_KEY, cube, data, list);
  }

  private void addRequesteeSubconstraint(ItemHypercube cube, PropertyMap data, List<Constraint> list) {
    if(statusStrictQuestion(data) && !hasSpecials(data, REQUESTEES_KEY)) {
      addSubconstraint(myRequesteeDescr, REQUESTEES_KEY, cube, data, list);
    }
  }

  private boolean statusStrictQuestion(PropertyMap data) {
    final List<ItemKey> aks = data.get(STATUSES_KEY);
    return aks != null && aks.size() == 1 && "?".equals(aks.get(0).getDisplayName());
  }

  private void addSubconstraint(
    BaseEnumConstraintDescriptor descr, TypedKey<List<ItemKey>> realKey,
    ItemHypercube cube, PropertyMap data, List<Constraint> list)
  {
    final Constraint c = createSubconstraint(descr, realKey, cube, data);
    if(c != null) {
      list.add(c);
    }
  }

  private Constraint createSubconstraint(
    BaseEnumConstraintDescriptor descr, TypedKey<List<ItemKey>> realKey,
    ItemHypercube cube, PropertyMap data)
  {
    final PropertyMap fake = createFakeMap(data, realKey);
    return descr.createConstraint(fake, cube);
  }

  private PropertyMap createFakeMap(PropertyMap data, TypedKey<List<ItemKey>> realKey) {
    final List<ItemKey> aks = data.get(realKey);
    final PropertyMap fake = new PropertyMap();
    if(aks != null && !aks.isEmpty()) {
      fake.put(SUBSET, removeSpecials(aks));
    }
    return fake;
  }

  private List<ItemKey> removeSpecials(List<ItemKey> aks) {
    List<ItemKey> replacement = null;

    int index = 0;
    for(final ItemKey ak : aks) {
      if(isSpecial(ak)) {
        if(replacement == null) {
          replacement = Collections15.arrayList();
          if(index > 0) {
            replacement.addAll(aks.subList(0, index));
          }
        }
      } else if(replacement != null) {
        replacement.add(ak);
      }
      index++;
    }

    if(replacement == null) {
      return aks;
    }

    if(replacement.isEmpty()) {
      return null;
    }

    return replacement;
  }

  private void addBlackBoxConstraint(PropertyMap data, List<Constraint> list) {
    if(!isSimplifiable(list, data)) {
      list.add(BlackBoxConstraint.INSTANCE);
    }
  }

  private boolean isSimplifiable(List<Constraint> simpler, PropertyMap data) {
    if(simpler.size() != 1) {
      // zero is not simplifiable because at least one flag must be present;
      // more than one is not simplifiable because a single flag must match all constraints
      return false;
    }

    if(hasSomething(data, STATUSES_KEY)) {
      if(statusStrictQuestion(data)
        && hasSomething(data, REQUESTEES_KEY)
        && !hasSpecials(data, REQUESTEES_KEY)) 
      {
        // because simpler.size() == 1, it's (status=? && requestee), and all
        // requestees are plain usernames, which is simplifiable to (requestee)
        return true;
      } else {
        // (a) there are other statuses, or (b) it has <nobody> in requestees,
        // or (c) it's (status && setter) or (status && type), anyway it's not simplifiable
        return false;
      }
    }

    return true;
  }

  private boolean hasSomething(PropertyMap data, TypedKey<List<ItemKey>> key) {
    final List<ItemKey> aks = data.get(key);
    return aks != null && !aks.isEmpty();
  }

  private Constraint composeSubconstraints(List<Constraint> list) {
    assert !list.isEmpty(); // at least, there'll be the "black box"
    if(list.size() == 1) {
      return list.get(0);
    } else {
      return CompositeConstraint.Simple.and(list);
    }
  }

  @Override
  public boolean isSameData(PropertyMap data1, PropertyMap data2) {
    return areSetsEqual(data1, data2, FLAG_TYPES_KEY, STATUSES_KEY, SETTERS_KEY, REQUESTEES_KEY);
  }

  private boolean areSetsEqual(PropertyMap data1, PropertyMap data2, TypedKey<List<ItemKey>>... keys) {
    for(final TypedKey<List<ItemKey>> key : keys) {
      if(!Comparing.areSetsEqual(data1.get(key), data2.get(key))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public CanvasRenderable getPresentation() {
    return this;
  }

  @Override
  public void writeFormula(FormulaWriter writer, String conditionId, PropertyMap data) {
    writer.addRaw("(");
    writer.addToken(conditionId);
    writer.addRaw(" (");
    writeSubformulas(writer, data);
    writer.addRaw("))");
  }

  private void writeSubformulas(FormulaWriter writer, PropertyMap data) {
    boolean notFirst = false;
    notFirst = writeSubformula(myFlagTypeDescr, FLAG_TYPES_KEY, writer, data, notFirst);
    notFirst = writeStatuses(writer, data, notFirst);
    notFirst = writeSubformula(mySetterDescr, SETTERS_KEY, writer, data, notFirst);
    writeSubformula(myRequesteeDescr, REQUESTEES_KEY, writer, data, notFirst);
  }

  private boolean writeSubformula(
    BaseEnumConstraintDescriptor descr, TypedKey<List<ItemKey>> realKey,
    FormulaWriter writer, PropertyMap data, boolean notFirst)
  {
    if(hasSomething(data, realKey)) {
      if(notFirst) {
        writer.addRaw("&");
      }

      putFakeSubset(data, realKey);
      descr.writeFormula(writer.createChild(), data);
      restoreSubset(data, realKey);
      return true;
    }
    return notFirst;
  }

  private boolean writeStatuses(FormulaWriter writer, PropertyMap data, boolean notFirst) {
    final List<ItemKey> aks = data.get(STATUSES_KEY);
    if(aks != null && !aks.isEmpty()) {
      if(notFirst) {
        writer.addRaw("&");
      }

      writer = writer.createChild();
      writer.addRaw(STATUSES_TOKEN);
      writer.addRaw(" ");

      final StringBuilder sb = new StringBuilder(3);
      for(final ItemKey ak : aks) {
        sb.append(m2d(ak.getDisplayName().charAt(0)));
      }
      writer.createChild().addToken(sb.toString());

      return true;
    }
    return notFirst;
  }

  @Override
  public PropertyMap getEditorData(PropertyMap data) {
    return getEditorData(myEditorKeys, data);
  }

  public static PropertyMap getEditorData(EditorKeys keys, PropertyMap data) {
    final PropertyMap result = new PropertyMap();
    keys.flagTypesKey.setInitialValue(result, data.get(FLAG_TYPES_KEY));
    keys.statusesKey.setInitialValue(result, data.get(STATUSES_KEY));
    keys.settersKey.setInitialValue(result, data.get(SETTERS_KEY));
    keys.requesteesKey.setInitialValue(result, data.get(REQUESTEES_KEY));
    return result;
  }

  @Override
  public void waitForInitialization() throws InterruptedException {
    myFlagTypeDescr.waitForInitialization();
    mySetterDescr.waitForInitialization();
    myRequesteeDescr.waitForInitialization();
  }

  @Override
  public String suggestName(String descriptorName, PropertyMap data, Map<TypedKey<?>, ?> hints)
    throws CannotSuggestNameException
  {
    final String name = formatConstraint(
      checkListLength(data.get(FLAG_TYPES_KEY)),
      checkListLength(data.get(STATUSES_KEY)),
      checkListLength(data.get(SETTERS_KEY)),
      checkListLength(data.get(REQUESTEES_KEY)));

    return checkStringLength(name, hints);
  }

  private List<ItemKey> checkListLength(List<ItemKey> list) throws CannotSuggestNameException {
    if(list != null && list.size() > 2) {
      throw new CannotSuggestNameException();
    }
    return list;
  }

  private String checkStringLength(String name, Map<TypedKey<?>, ?> hints) throws CannotSuggestNameException {
    final Integer maxLength = UserQueryNode.MAX_NAME_LENGTH.getFrom(hints);
    if(maxLength != null && name.length() > maxLength) {
      throw new CannotSuggestNameException();
    }
    return name;
  }

  @Override
  public void renderOn(Canvas canvas, CellState state) {
    canvas.setIcon(FLAGS_ICON);
    canvas.appendText(getDisplayName());
  }

  static class EditorKeys {
    final SubsetKey flagTypesKey;
    final SubsetKey statusesKey;
    final SubsetKey settersKey;
    final SubsetKey requesteesKey;

    EditorKeys() {
      flagTypesKey = new SubsetKey(TYPES_TOKEN, TYPES_TOKEN);
      statusesKey = new SubsetKey(STATUSES_TOKEN, STATUSES_TOKEN);
      settersKey = new SubsetKey(SETTERS_TOKEN, SETTERS_TOKEN);
      requesteesKey = new SubsetKey(REQUESTEES_TOKEN, REQUESTEES_TOKEN);
    }

    SubsetKey[] toArray() {
      return new SubsetKey[] { flagTypesKey, statusesKey, settersKey, requesteesKey };
    }
  }

  public static PropertyMap createDescriptorData(
    @Nullable List<ItemKey> flagTypes, @Nullable List<ItemKey> statuses,
    @Nullable List<ItemKey> setters, @Nullable List<ItemKey> requestees)
  {
    final PropertyMap data = new PropertyMap();
    putIfNotNull(data, FLAG_TYPES_KEY, flagTypes);
    putIfNotNull(data, STATUSES_KEY, statuses);
    putIfNotNull(data, SETTERS_KEY, setters);
    putIfNotNull(data, REQUESTEES_KEY, requestees);
    return data;
  }

  private static <T> void putIfNotNull(PropertyMap map, TypedKey<T> key, T value) {
    if(value != null) {
      map.put(key, value);
    }
  }

  public static String formatConstraint(
    @Nullable List<ItemKey> flagTypes, @Nullable List<ItemKey> statuses,
    @Nullable List<ItemKey> setters, @Nullable List<ItemKey> requestees)
  {
    final StringBuilder sb = new StringBuilder();
    sb.append(formatList(setters, null, ": ", ", ", null, "setters"));
    sb.append(formatList(flagTypes, null, null, ", ", "<Any Flag>", "flags"));
    sb.append(formatList(statuses, null, null, "", null, "statuses"));
    sb.append(formatList(requestees, " (", ")", ", ", null, "requestees"));
    return sb.toString();
  }

  private static String formatList(List<ItemKey> list, String before, String after, String delim, String empty, String many) {
    if(list == null || list.isEmpty()) {
      return Util.NN(empty);
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(Util.NN(before));
    final int size = list.size();
    if(size == 1) {
      sb.append(list.get(0).getDisplayName());
    } else if(size == 2) {
      sb.append("{").append(StringUtil.implode(ItemKey.DISPLAY_NAME.collectList(list), delim)).append("}");
    } else {
      sb.append("{").append(size).append(" ").append(many).append("}");
    }
    sb.append(Util.NN(after));
    return sb.toString();
  }

  public static void registerParsers(TokenRegistry<FilterNode> registry) {
    registry.registerFunction(FLAGS_TOKEN, new FlagsParser());
    registry.registerFunction(STATUSES_TOKEN, new StatusesParser());
  }

  public static boolean isLegalStatusChar(char c) {
    return "+-?".indexOf(c) >= 0;
  }

  public static boolean isLegalStatusString(String s) {
    final int length = s.length();
    if(length < 1 || length > 2) {
      return false;
    }
    for(int i = 0; i < length; i++) {
      if(!isLegalStatusChar(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static char m2d(char c) {
    return c == '\u2212' ? '-' : c;
  }

  public static char d2m(char c) {
    return c == '-' ? '\u2212' : c;
  }
}
