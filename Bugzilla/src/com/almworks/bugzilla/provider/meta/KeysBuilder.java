package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.util.*;
import com.almworks.api.engine.Connection;
import com.almworks.api.explorer.gui.*;
import com.almworks.api.syncreg.ItemHypercube;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.KeywordLink;
import com.almworks.bugzilla.provider.datalink.VotesLink;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.ItemVersion;
import com.almworks.util.Pair;
import com.almworks.util.Terms;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.Comparing;
import com.almworks.util.collections.Containers;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.components.renderer.Renderers;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.images.Icons;
import com.almworks.util.models.ColumnSizePolicy;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.DocumentAdapter;
import com.almworks.util.ui.swing.DocumentUtil;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * @author dyoma
 */
// review(IS,2008-03-27): review later (keep this comment)
class KeysBuilder {
  private final List<ModelKey<?>> myKeys = Collections15.arrayList();
  private final Map<DBAttribute, ModelKey<?>> myKeyMap = Collections15.hashMap();

  private static final StateIcon VOTED_ICON =
    new StateIcon(Icons.VOTED, 0, "You have voted for this " + Terms.ref_artifact);

  public ComboBoxModelKey addComboBox(SingleEnumAttribute enumAttr, boolean allowNull) {
    ItemKey nullValue = allowNull ? ItemKeyStub.ABSENT : null;
    TextResolver resolver = enumAttr.getEnumType().getResolver();
    ComboBoxModelKey key = resolver != null ? new EditableComboBoxModelKey(enumAttr, resolver, nullValue) : new ComboBoxModelKey(enumAttr, nullValue);
    return addKey(key, enumAttr.getBugAttribute());
  }

  public ComboBoxModelKey addStatus() {
    return addKey(new StatusKey(), SingleEnumAttribute.STATUS.getBugAttribute());
  }

  public ComboBoxModelKey addProductDependent(ComboBoxModelKey productKey, SingleEnumAttribute enumAttr) {
    return addKey(new ProductDependentKey(productKey, enumAttr), enumAttr.getBugAttribute());
  }

  public ComboBoxModelKey addResolutionComboBox() {
    final SingleEnumAttribute enumAttr = SingleEnumAttribute.RESOLUTION;
    ComboBoxModelKey key = new ComboBoxModelKey(SingleEnumAttribute.RESOLUTION, ItemKeyStub.ABSENT) {
      public Pair<String, ExportValueType> formatForExport(PropertyMap values, NumberFormat numberFormat,
        DateFormat dateFormat, boolean htmlAccepted)
      {
        Pair<String, ExportValueType> result = super.formatForExport(values, numberFormat, dateFormat, htmlAccepted);
        if (result != null && result.getSecond() == ExportValueType.STRING) {
          String defValue = enumAttr.getEnumType().getDefaultValue();
          if (defValue != null && defValue.equalsIgnoreCase(result.getFirst())) {
            return Pair.create("", ExportValueType.STRING);
          }
        }
        return result;
      }

      @Override
      protected boolean isEqualValue(ItemKey value1, ItemKey value2) {
        value1 = normalizeNA(value1);
        value2 = normalizeNA(value2);
        return super.isEqualValue(value1, value2);
      }

      private ItemKey normalizeNA(ItemKey value) {
        if (value != null && BugzillaAttribute.NOT_AVAILABLE.equals(value.getId())) value = null;
        if (value == ItemKeyStub.ABSENT) value = null;
        return value;
      }
    };
    return addKey(key, enumAttr.getBugAttribute());
  }


  public <K extends ModelKey> K addKey(K key, DBAttribute ptr) {
    myKeys.add(key);
    if (ptr != null) {
      myKeyMap.put(ptr, key);
    }
    return key;
  }

  public List<ModelKey<?>> getKeys() {
    return myKeys;
  }

  public Map<DBAttribute, ModelKey<?>> getKeyMap() {
    return myKeyMap;
  }

  public ValueKey<Integer> addID() {
    return addInt(BugzillaAttribute.ID, Bug.attrBugID);
  }

  public ValueKey<Integer> addInt(BugzillaAttribute bzAttribute, DBAttribute<Integer> attribute) {
    String displayableName = BugzillaUtil.getDisplayableFieldName(bzAttribute);
    return addKey(ValueKey.createComparableValue(attribute, true, displayableName), attribute);
  }

  public SimpleModelKey<Document, BigDecimal> addDecimalDocumentKey(
    BugzillaAttribute bzAttr, DBAttribute<BigDecimal> dbAttr, boolean exportable)
  {
    final String dn = BugzillaUtil.getDisplayableFieldName(bzAttr);
    final SimpleModelKey<Document, BigDecimal> mk = exportable ? 
      SimpleModelKey.decimal(dbAttr, dn) : SimpleModelKey.decimalUnexportable(dbAttr, dn);
    return addKey(mk, dbAttr);
  }

  public SimpleModelKey<Document, String> addTextDocumentKey(BugzillaAttribute bzAttribute, DBAttribute<String> attribute) {
    return addKey(
      SimpleModelKey.text(attribute, BugzillaUtil.getDisplayableFieldName(bzAttribute), bzAttribute.isMultilineExport()),
      attribute);
  }

  public SimpleModelKey<StyledDocument, String> addStyledTextDocumentKey(BugzillaAttribute bzAttribute, DBAttribute<String> attribute) {
    return addKey(SimpleModelKey.styledText(attribute, BugzillaUtil.getDisplayableFieldName(bzAttribute),
      bzAttribute.isMultilineExport()), attribute);
  }

  public ItemsListKey addBugListValue(BugzillaAttribute bzAttribute, DBAttribute<Set<Long>> attribute) {
    ItemsListKey key =
      new ItemsListKey(attribute, CommonMetadata.bugResolver, BugzillaUtil.getDisplayableFieldName(bzAttribute)) {
        @Override
        protected ItemKey extractValueFrom(Long item, ItemVersion primaryItem, LoadedItemServices itemServices) {
          if (item == null || primaryItem == null) {
            assert false : item + " " + primaryItem;
            return ItemKeyStub.ABSENT;
          }
          ItemVersion trunk = primaryItem.forItem(item);
          Integer id = trunk.getValue(Bug.attrBugID);
          if (id == null) {
            // "delete invalid bug" on a bug referenced in another bug's Blocks/Depends will make it fail
            // assert false : item;
            return ItemKeyStub.ABSENT;
          }
          return ResolvedItem.create(trunk, id.toString());
        }

        public boolean isEqualValue(ModelMap models, PropertyMap values) {
          return Comparing.areSetsEqual(getValue(models), getValue(values));
        }
      };
    return addKey(key, attribute);
  }

  public ModelKey<Set<ItemKey>> createKeywordsKey() {
    BaseKeyBuilder<Set<ItemKey>> builder = BaseKeyBuilder.create();
    final DBAttribute<Set<Long>> attribute = Bug.attrKeywords;
    builder.setMergePolicy(ModelMergePolicy.MANUAL);
    builder.setValueRenderer(Renderers.<ItemKey>canvasDefaultSet("", " "));
    builder.setName(attribute.getName());
    builder.setDisplayName(BugzillaUtil.getDisplayableFieldName(BugzillaAttribute.KEYWORDS));
    builder.setExport(BaseModelKey.Export.RENDER);
    builder.setIO(new KeywordsIO());
    return addKey(builder.getKey(), attribute);
  }

  public ModelKey<BigDecimal> addDecimalDocumentKey(
    BugzillaAttribute bza, DBAttribute<BigDecimal> a, ModelMergePolicy mergePolicy, boolean exportable)
  {
    SimpleModelKey<Document, BigDecimal> mk = addDecimalDocumentKey(bza, a, exportable);
    mk.setMergePolicy(mergePolicy);
    return mk;
  }

  private static class KeywordsIO extends BaseKeyBuilder.SimpleRefSetIO {
    public KeywordsIO() {
      super(Bug.attrKeywords, CommonMetadata.keywordLink, null,
        CommonMetadata.getEnumDescriptor(BugzillaAttribute.KEYWORDS));
    }

    @Override
    protected Set<Long> toDatabaseValue(UserChanges changes, Set<ItemKey> userInput) {
      final Connection connection = changes.getConnection();
      if(!(connection instanceof BugzillaConnection)) {
        return emptyDbCollection();
      }

      if (userInput == null) return null;
      else if (userInput.isEmpty()) return emptyDbCollection();
      else {
        final PrivateMetadata privateMetadata =
          ((BugzillaContext) ((BugzillaConnection) connection).getContext()).getPrivateMetadata();
        final KeywordLink link = CommonMetadata.keywordLink;
        final Set<Long> result = Collections15.hashSet(userInput.size());
        for (final ItemKey artifactKey : userInput) {
          final String keyword = artifactKey.getId();
          result.add(link.getOrCreateReferent(privateMetadata, keyword, changes.getCreator()));
        }
        return result;
      }
    }

    @Override
    public <SM> SM getModel(final Lifespan life, final ModelMap modelMap, final ModelKey<Set<ItemKey>> key,
      Class<SM> aClass)
    {
      if (aClass.isAssignableFrom(PlainDocument.class)) {
        final PlainDocument document = new PlainDocument();
        final ItemHypercube hypercube = LoadedItemServices.VALUE_KEY.getValue(modelMap).getConnectionCube();
        final AListModel<ItemKey> listModel = myConstraintType.getEnumModel(life, hypercube);

        DocumentUtil.addListener(life, document, new KeywordListener(document, listModel, key, modelMap));
        List<String> stringList = ItemKey.DISPLAY_NAME.collectList(getList(modelMap, key));
        DocumentUtil.setDocumentText(document, TextUtil.separate(stringList, " "));

        return (SM) document;
      } else {
        return super.getModel(life, modelMap, key, aClass);
      }
    }
  }

  private static class KeywordListener extends DocumentAdapter {
    private final PlainDocument myDocument;
    private final AListModel<ItemKey> myListModel;
    private final ModelKey<Set<ItemKey>> myKey;
    private final ModelMap myModelMap;
    private final PropertyMap myProps = new PropertyMap();

    public KeywordListener(PlainDocument document, AListModel<ItemKey> listModel, ModelKey<Set<ItemKey>> key,
      ModelMap modelMap)
    {
      myDocument = document;
      myListModel = listModel;
      myKey = key;
      myModelMap = modelMap;
    }

    protected void documentChanged(DocumentEvent e) {
      String[] strings = getKeywords(myDocument);
      if (strings != null) {
        Set<ItemKey> keys = Collections15.linkedHashSet();
        for (final String string : strings) {
          ItemKey keyword = contains(myListModel, string);
          if (keyword != null) {
            keys.add(keyword);
          }
        }
        myKey.setValue(myProps, keys);
        myKey.copyValue(myModelMap, myProps);
      }
    }

    @Nullable
    private String[] getKeywords(PlainDocument document) {
      try {
        String s = document.getText(0, document.getLength());
        return TextUtil.getKeywords(s);
      } catch (BadLocationException e) {
        Log.warn(e);
      }
      return null;
    }

    private ItemKey contains(AListModel<ItemKey> listModel, String string) {
      for (int i = 0; i < listModel.getSize(); i++) {
        ItemKey at = listModel.getAt(i);
        if (at.getId().equals(string)) {
          return at;
        }
      }
      return new ItemKeyStub(string.toLowerCase(), string, ItemOrder.NO_ORDER);
    }
  }

  public ModelKey<Date> addDate(BugzillaAttribute bzAttribute, DBAttribute<Date> attribute) {
    BaseKeyBuilder<Date> builder = BaseKeyBuilder.create();
    builder.setAccessor(new BaseModelKey.SimpleDataAccessor<Date>(bzAttribute.getName()));
    builder.setComparator(Containers.<Date>comparablesComparator());
    builder.setDisplayName(BugzillaUtil.getDisplayableFieldName(bzAttribute));
    builder.setExport(BaseModelKey.Export.DATE);
    builder.setHeaderText(BugzillaUtil.getDisplayableFieldName(bzAttribute));
    builder.setIO(new DateSimpleIO(attribute));
    builder.setMergePolicy(ModelMergePolicy.MANUAL);
    builder.setName(bzAttribute.getName());
    builder.setValueRenderer(new CanvasRenderer<Date>() {
      public void renderStateOn(CellState state, Canvas canvas, Date item) {
        if (item != null)
          canvas.appendText(DateUtil.toLocalDate(item));
      }
    });
    return addKey(builder.getKey(), attribute);
  }

  public ModelKey<List<ItemKey>> addVotersListKey() {
    // todo bugs!
    BaseKeyBuilder<List<ItemKey>> builder = BaseKeyBuilder.create();
    final DBAttribute<List<Long>> attribute = VotesLink.votesUserList;
    builder.setDisplayName("Voters");
    builder.setMergePolicy(ModelMergePolicy.COPY_VALUES);
    builder.setPromotionPolicy(DataPromotionPolicy.ALWAYS);
    builder.setName("voter_list");
    CanvasRenderer<List<ItemKey>> canvasRenderer = Renderers.<ItemKey>canvasDefaultList("", ", ");
    builder.setValueRenderer(canvasRenderer);
    builder.setSimpleRefListIO(attribute, User.KEY_FACTORY, null,
      CommonMetadata.getEnumDescriptor(BugzillaAttribute.ASSIGNED_TO), null, true);

    ModelKey<List<ItemKey>> modelKey = builder.getKey();
    addKey(modelKey, attribute);
    return modelKey;
  }

  public ModelKey<Integer> addVoteValue(DBAttribute<Integer> attr, String displayableName) {
    return addKey(ValueKey.createComparableValue(attr, true, displayableName), attr);
  }

  public ModelKey<Integer> addMyVotesValue(DBAttribute<Integer> attr, String displayableName) {
    ValueKey<Integer> key = new ValueKey<Integer>(attr, Containers.<Integer>comparablesComparator(), true, displayableName) {
      @Override
      public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values) {
        super.extractValue(itemVersion, itemServices, values);
        Integer v = getValue(values);
        if (v != null && v > 0) {
          StateIconHelper.addStateIcon(values, VOTED_ICON);
        }
      }
    };
    return addKey(key, attr);
  }

  public ModelKey<Integer> addVoteProductValue(final DBAttribute<Integer> attribute, String s) {
    BaseKeyBuilder builder = BaseKeyBuilder.create();
    builder.setDisplayName(s);
    builder.setName(attribute.getName());
    builder.setSizePolicy(ColumnSizePolicy.FIXED);
    builder.setMergePolicy(ModelMergePolicy.COPY_VALUES);
    builder.setPromotionPolicy(DataPromotionPolicy.ALWAYS);
    builder.setIO(new BaseModelKey.DataIO<Integer>() {
      public void extractValue(ItemVersion itemVersion, LoadedItemServices itemServices, PropertyMap values, ModelKey<Integer> modelKey) {
        Long product = itemVersion.getValue(Bug.attrProduct);
        if (product == null || product.longValue() <= 0) {
          modelKey.setValue(values, 0);
        } else {
          Integer value = itemVersion.getReader().getValue(product, attribute);
          if (value != null && value.intValue() > 0) {
            modelKey.setValue(values, value);
          }
        }
      }

      public void addChanges(UserChanges changes, ModelKey modelKey) {
      }

      public Object getModel(Lifespan life, ModelMap model, ModelKey modelKey, Class aClass) {
        return null;
      }
    });
    ModelKey modelKey = builder.getKey();
    addKey(modelKey, Bug.attrProduct);
    return modelKey;
  }

  public ModelKey<List<Integer>> addVotesValuesList() {
    BaseKeyBuilder<List<Integer>> builder = BaseKeyBuilder.create();
    final DBAttribute<List<Integer>> attr = VotesLink.votesValueList;
    builder.setDisplayName("Individual votes");
    builder.setPromotionPolicy(DataPromotionPolicy.ALWAYS);
    builder.setMergePolicy(ModelMergePolicy.COPY_VALUES);
    builder.setName("voter_values");

    builder.setIO(new BaseSimpleIO.ListIO<Integer, Integer>(attr) {
      protected List<Integer> extractValue(List<Integer> dbValue, ItemVersion version, LoadedItemServices itemServices) {
        if(dbValue == null || dbValue.isEmpty()) {
          return Collections15.emptyList();
        }
        return Collections15.arrayList(dbValue);
      }

      protected List<Integer> toDatabaseValue(UserChanges changes, List<Integer> userInput) {
        return userInput;
      }
    });

    ModelKey<List<Integer>> modelKey = builder.getKey();
    addKey(modelKey, attr);
    return modelKey;
  }
}