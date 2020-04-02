package com.almworks.engine.gui;

import com.almworks.api.application.*;
import com.almworks.api.application.field.ItemField;
import com.almworks.api.application.viewer.UIController;
import com.almworks.api.engine.Connection;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.commons.Function;
import com.almworks.util.commons.Function2;
import com.almworks.util.components.renderer.*;
import com.almworks.util.components.renderer.table.*;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.actions.*;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;
import org.almworks.util.detach.Lifespan;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author dyoma
 */
public class LeftFieldsBuilder extends ItemTableBuilder {
  public static final TypedKey<ModelMap> MODEL_MAP = TypedKey.create("modelMap");
  public static final UIController<RendererHostComponent> CONTROLLER = new RendererHostConstroller();
  private final TableRenderer myRenderer = new TableRenderer();

  public TableRenderer getPresentation() {
    return myRenderer;
  }

  public LineBuilder createLineBuilder(String label) {
    LineBuilder builder = new LineBuilderImpl(myRenderer);
    builder.setLabel(label);
    return builder;
  }

  public void addItemLines(ModelKey<List<ModelKey<?>>> customFields,
    final Function2<ModelKey, ModelMap, ItemField> fieldGetter)
  {
    addLine(new MultilineDelegatorLine(myRenderer, customFields, fieldGetter) {
      protected List<TableRendererLine> createLines(RendererContext context) {
        ModelMap modelMap = getModelMapFromContext(context);
        if (modelMap == null) return Collections15.emptyList();
        List<ModelKey<?>> cfKeys = getModelValueFromContext(context, myKey);
        List<TableRendererLine> result = Collections15.arrayList();
        if (cfKeys != null) {
          for (int i = 0; i < cfKeys.size(); i++) {
            ModelKey<?> key = cfKeys.get(i);
            if (!hasModelValueFromContext(context, key))
              continue;
            ItemField<?, ?> field = fieldGetter.invoke(key, modelMap);
            if (field == null)
              continue;
            TableRendererCell viewerCell = field.getViewerCell(modelMap, myRenderer);
            if (viewerCell != null) {
              result.add(TwoColumnLine.labeledCell(key.getDisplayableName() + ":", viewerCell, myRenderer));
            }
          }
        }
        return result;
      }
    });
  }

  public void addLine(String s, final Function<ModelMap, String> valueGetter,
    final Function<ModelMap, Boolean> visibilityFunction)
  {
    TwoColumnLine line = addLine(s, new TextCell(FontStyle.BOLD, new ModelMapFunctionAdapter<String>(valueGetter)));
    line.setVisibility(new ModelMapFunctionAdapter<Boolean>(visibilityFunction));
  }


  public <T extends ItemKey> void addItem(String label, ModelKey<T> key, Function<T, Boolean> visibility) {
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, new ItemTextGetter(key)));
    if (visibility != null)
      line.setVisibility(new VisibleAdapter(key, visibility));
  }


  public void addString(String label, final ModelKey<String> key, boolean hideEmpty) {
    TextTextGetter getter = new TextTextGetter(key);
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, getter));
    if (hideEmpty) {
      line.setVisibility(new VisibleIfNotEmpty(getter));
    }
  }


  public void addString(String label, final ModelKey<String> key, Function<String, Boolean> visibility) {
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, new TextTextGetter(key)));
    if (visibility != null) {
      line.setVisibility(new VisibleAdapter(key, visibility));
    }
  }

  public void addItemList(
    String label, final ModelKey<? extends Collection<? extends ItemKey>> key,
    boolean hideEmpty, Comparator<? super ItemKey> order)
  {
    TwoColumnLine line = myRenderer.addLine(label, new ItemKeyValueListCell(key, order, myRenderer));
    if (hideEmpty) {
      line.setVisibility(new NotEmptyCollection(key));
    }
  }

  @Override
  public void addStringList(String label, ModelKey<List<String>> key, boolean hideEmpty,
    @Nullable Function<Integer, List<CellAction>> cellActions, @Nullable List<CellAction> aggregateActions,
    @Nullable Function2<ModelMap, String, String> elementConvertor)
  {
    TableRendererCell cell = new StringValueListCell(key, myRenderer, cellActions, elementConvertor);
    if (aggregateActions != null) {
      cell = new ActionCellDecorator(cell, aggregateActions);
    }
    TwoColumnLine line = myRenderer.addLine(label, cell);
    if (hideEmpty) {
      line.setVisibility(new NotEmptyCollection(key));
    }
  }

  public void addDate(String label, final ModelKey<Date> key, boolean hideEmpty, boolean showTime,
    boolean showTimeOnlyIfExists)
  {
    final DateTextGetter getter = new DateTextGetter(key, showTime, showTimeOnlyIfExists);
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, getter));
    if (hideEmpty) {
      line.setVisibility(new VisibleIfNotEmpty(getter));
    }
  }

  public void addSecondsDuration(String label, ModelKey<Integer> modelKey, boolean hideEmpty) {
    final SecondsDurationGetter getter = new SecondsDurationGetter(modelKey);
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, getter));
    if (hideEmpty) {
      line.setVisibility(new VisibleIfNotEmpty(getter));
    }
  }

  public void addInteger(String label, final ModelKey<Integer> key) {
    IntegerTextGetter getter = new IntegerTextGetter(key);
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, getter));
    line.setVisibility(new VisibleIfNotZero(getter));
  }

  public void addDecimal(String label, final ModelKey<BigDecimal> key, boolean hideZeroOrEmpty) {
    final DecimalTextGetter getter = new DecimalTextGetter(key);
    TwoColumnLine line = myRenderer.addLine(label, new TextCell(FontStyle.BOLD, getter));
    if (hideZeroOrEmpty) {
      line.setVisibility(new VisibleIfNotZero(getter));
    }
  }

  public TableRendererLine addLine(TableRendererLine line) {
    return myRenderer.addLine(line);
  }

  public TwoColumnLine addLine(String label, TableRendererCell cell) {
    return myRenderer.addLine(label, cell);
  }

  public void addSeparator() {
    myRenderer.addLine(new SeparatorLine(5));
  }


  private static class RendererHostConstroller implements UIController<RendererHostComponent> {
    public void connectUI(@NotNull Lifespan lifespan, @NotNull ModelMap model,
      @NotNull final RendererHostComponent component)
    {
      component.putValue(lifespan, MODEL_MAP, model);
      ChangeListener listener = new ChangeListener() {
        public void onChange() {
          component.invalidateRenderer();
        }
      };
      model.addAWTChangeListener(lifespan, listener);
      listener.onChange();
    }
  }


  public static class LineBuilderImpl implements LineBuilder {
    private final TableRenderer myRenderer;
    private final java.util.List<CellAction> myActions = Collections15.arrayList();
    private TableRendererCell myValueCell;
    private Function<RendererContext, Boolean> myVisibility;
    private TableRendererCell myLabelCell;

    public LineBuilderImpl(TableRenderer renderer) {
      myRenderer = renderer;
    }

    public LineBuilder setLabel(String label) {
      assert label != null;
      return setLabelCell(TextCell.label(label));
    }

    private LineBuilder setLabelCell(TableRendererCell cell) {
      assert cell != null;
      myLabelCell = cell;
      return this;
    }

    public LineBuilder setStringValue(ModelKey<String> key, boolean hideEmpty) {
      TextTextGetter getter = new TextTextGetter(key);
      myValueCell = new TextCell(FontStyle.BOLD, getter);
      if (hideEmpty)
        myVisibility = new VisibleIfNotEmpty(getter);
      return this;
    }

    public LineBuilder setValueCell(TableRendererCell cell) {
      myValueCell = cell;
      return this;
    }

    public LineBuilder setItemListValue(ModelKey<Collection<ItemKey>> key, boolean hideEmpty) {
      myValueCell = new ItemKeyValueListCell(key, myRenderer);
      if (hideEmpty)
        myVisibility = new NotEmptyCollection(key);
      return this;
    }

    public LineBuilder addAction(String tooltip, Icon icon, AnActionListener action) {
      myActions.add(new CellAction(icon, tooltip, action));
      return this;
    }

    public void addLine() {
      assert myLabelCell != null;
      assert myValueCell != null;
      if (!myActions.isEmpty()) {
        myValueCell = new ActionCellDecorator(myValueCell, myActions);
      }
      TwoColumnLine line = new TwoColumnLine(myLabelCell, myValueCell, myRenderer);
      myRenderer.addLine(line);
      if (myVisibility != null)
        line.setVisibility(myVisibility);
    }

    public LineBuilder setVisibility(Function<RendererContext, Boolean> visibility) {
      myVisibility = visibility;
      return this;
    }

    public LineBuilder setItemValue(ModelKey<ItemKey> key) {
      myValueCell = new TextCell(FontStyle.BOLD, new ItemTextGetter(key));
      return this;
    }

    public LineBuilder setIntegerValue(ModelKey<Integer> key) {
      myValueCell = new TextCell(FontStyle.BOLD, new IntegerTextGetter(key));
      return this;
    }
  }


  public static class NotEmptyCollection implements Function<RendererContext, Boolean> {
    private final ModelKey<? extends Collection<?>> myKey;

    public NotEmptyCollection(ModelKey<? extends Collection<?>> key) {
      myKey = key;
    }

    public Boolean invoke(RendererContext context) {
      Collection<?> value = getModelValueFromContext(context, myKey);
      return value != null && value.size() > 0;
    }
  }


  public static abstract class Action<T> implements AnActionListener {
    private final ModelKey<T> myKey;

    public Action(ModelKey<T> key) {
      myKey = key;
    }

    public void perform(ActionContext context) throws CantPerformException {
      RendererContext rendererContext = context.getSourceObject(RendererContext.RENDERER_CONTEXT);
      T value = getModelValueFromContext(rendererContext, myKey);
      if (value == null)
        return;
      LoadedItemServices lis = getModelValueFromContext(rendererContext, LoadedItemServices.VALUE_KEY);
      Connection connection = lis.getConnection();
      if (connection == null) {
        return;
      }
      act(context, rendererContext, connection, value);
    }

    protected abstract void act(ActionContext context, RendererContext rendererContext, @NotNull Connection connection,
      @NotNull T value) throws CantPerformException;
  }

  @Nullable
  public static ModelMap getModelMapFromContext(RendererContext context) {
    return context.getValue(MODEL_MAP);
  }

  public static <T> T getModelValueFromContext(RendererContext context, ModelKey<T> modelKey) {
    ModelMap modelMap = getModelMapFromContext(context);
    return modelMap != null ? modelKey.getValue(modelMap) : null;
  }

  public static boolean hasModelValueFromContext(RendererContext context, ModelKey<?> key) {
    ModelMap modelMap = getModelMapFromContext(context);
    return modelMap != null && key.hasValue(modelMap);
  }

  public static class TextTextGetter implements Function<RendererContext, String> {
    private final ModelKey<String> myKey;

    public TextTextGetter(ModelKey<String> key) {
      myKey = key;
    }

    public String invoke(RendererContext argument) {
      return getModelValueFromContext(argument, myKey);
    }
  }

  public static class DateTextGetter implements Function<RendererContext, String> {
    private final ModelKey<Date> myKey;
    private final boolean myShowTime;
    private final boolean myShowTimeOnlyIfExists;

    public DateTextGetter(ModelKey<Date> key, boolean showTime, boolean showTimeOnlyIfExists) {
      myKey = key;
      myShowTime = showTime;
      myShowTimeOnlyIfExists = showTimeOnlyIfExists;
    }

    public String invoke(RendererContext context) {
      Date date = getModelValueFromContext(context, myKey);
      if (date == null)
        return null;
      return myShowTime ?
        (myShowTimeOnlyIfExists ? DateUtil.toLocalDateAndMaybeTime(date, null) : DateUtil.toLocalDateTime(date)) :
        DateUtil.toLocalDate(date);
    }
  }


  public static class IntegerTextGetter implements Function<RendererContext, String> {
    private final ModelKey<Integer> myKey;

    public IntegerTextGetter(ModelKey<Integer> key) {
      myKey = key;
    }

    public String invoke(RendererContext context) {
      Integer integer = getModelValueFromContext(context, myKey);
      return integer != null ? integer.toString() : "";
    }
  }


  public static class SecondsDurationGetter implements Function<RendererContext, String> {
    private final ModelKey<Integer> myKey;

    public SecondsDurationGetter(ModelKey<Integer> key) {
      myKey = key;
    }

    public String invoke(RendererContext context) {
      Integer seconds = getModelValueFromContext(context, myKey);
      if (seconds == null)
        return "";
      return DateUtil.getFriendlyDuration(seconds, true);
    }
  }


  public static class BooleanTextGetter implements Function<RendererContext, String> {
    private final ModelKey<Boolean> myKey;

    public BooleanTextGetter(ModelKey<Boolean> key) {
      myKey = key;
    }

    public String invoke(RendererContext context) {
      Boolean integer = getModelValueFromContext(context, myKey);
      return integer != null ? String.valueOf(integer) : "";
    }
  }


  private class DecimalTextGetter implements Function<RendererContext, String> {
    private final ModelKey<BigDecimal> myKey;

    public DecimalTextGetter(ModelKey<BigDecimal> key) {
      myKey = key;
    }

    public String invoke(RendererContext context) {
      BigDecimal value = getModelValueFromContext(context, myKey);
      return value != null ? TextUtil.bigDecimalToString(value) : "";
    }
  }


  private static class VisibleIfNotZero implements Function<RendererContext, Boolean> {

    private Function<RendererContext, String> myGetter;

    public VisibleIfNotZero(Function<RendererContext, String> getter) {
      myGetter = getter;
    }

    public Boolean invoke(RendererContext context) {
      String string = myGetter.invoke(context);
      return string != null && string.length() > 0 && !string.equals("0");
    }
  }


  private static class VisibleIfNotEmpty implements Function<RendererContext, Boolean> {
    private final Function<RendererContext, String> myGetter;

    public VisibleIfNotEmpty(Function<RendererContext, String> getter) {
      myGetter = getter;
    }

    public Boolean invoke(RendererContext argument) {
      String value = myGetter.invoke(argument);
      return value != null && value.length() > 0;
    }
  }


  private static class VisibleAdapter<T> implements Function<RendererContext, Boolean> {
    private final Function<T, Boolean> myVisibility;
    private ModelKey<T> myKey;

    public VisibleAdapter(ModelKey<T> key, Function<T, Boolean> visibility) {
      myVisibility = visibility;
      myKey = key;
    }

    public Boolean invoke(RendererContext argument) {
      T value = getModelValueFromContext(argument, myKey);
      return myVisibility.invoke(value);
    }
  }


  public static class ItemTextGetter implements Function<RendererContext, String> {

    private final ModelKey<? extends ItemKey> myKey;

    public ItemTextGetter(ModelKey<? extends ItemKey> key) {
      myKey = key;
    }

    public String invoke(RendererContext context) {
      ItemKey value = getModelValueFromContext(context, myKey);
      return value != null ? value.getDisplayName() : null;
    }
  }


  private class ModelMapFunctionAdapter<M> implements Function<RendererContext, M> {
    private Function<ModelMap, M> myDelegate;

    private ModelMapFunctionAdapter(Function<ModelMap, M> delegate) {
      myDelegate = delegate;
    }

    public M invoke(RendererContext argument) {
      ModelMap modelMap = getModelMapFromContext(argument);
      return modelMap != null ? myDelegate.invoke(modelMap) : null;
    }
  }
}