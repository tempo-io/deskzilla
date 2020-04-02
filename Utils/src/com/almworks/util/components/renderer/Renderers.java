package com.almworks.util.components.renderer;

import com.almworks.util.collections.Convertor;
import com.almworks.util.components.*;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.text.TextUtil;
import com.almworks.util.ui.UIUtil;
import org.almworks.util.Const;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author dyoma
 */
public class Renderers {
  public static final CanvasRenderer<BigDecimal> BIG_DECIMAL_RENDERER = new CanvasRenderer<BigDecimal>() {
    public void renderStateOn(CellState state, Canvas canvas, BigDecimal item) {
      if (item != null)
        canvas.appendText(TextUtil.bigDecimalToString(item));
    }
  };
  public static final CanvasRenderer<BigDecimal> NEGATING_BIG_DECIMAL_RENDERER = new CanvasRenderer<BigDecimal>() {
    public void renderStateOn(CellState state, Canvas canvas, BigDecimal item) {
        if (item != null)
        canvas.appendText(TextUtil.bigDecimalToString(item.negate()));
    }
  };


  private static final DefaultCanvasRenderer<Object> DEFAULT_CANVAS_RENDERER =
    new DefaultCanvasRenderer<Object>(CanvasRenderable.EMPTY);
  private static final ToStringRenderer<Object> TOSTRING_RENDERER = new ToStringRenderer<Object>();

  public static <T> CollectionRenderer<T> createRenderer(final CanvasRenderer<T> renderer) {
    return new DefaultCollectionRenderer<T>(renderer);
  }

  public static <T> CollectionRenderer<T> createToString() {
    return createRenderer(Renderers.<T>canvasToString());
  }

  public static <T> ToStringRenderer<T> canvasToString() {
    return (ToStringRenderer<T>) TOSTRING_RENDERER;
  }

  public static <T> CollectionRenderer<T> createDefaultRenderer(final CanvasRenderable nullPresentation) {
    return createRenderer(new DefaultCanvasRenderer<T>(nullPresentation));
  }

  public static <T> CanvasRenderer<T> defaultCanvasRenderer() {
    return (CanvasRenderer<T>) DEFAULT_CANVAS_RENDERER;
  }

  public static <T> CollectionRenderer<T> createDefaultRenderer() {
    return createDefaultRenderer(CanvasRenderable.EMPTY);
  }

  public static CanvasRenderable text(final String text) {
    return new TextRenderable(text);
  }

  public static <T> CanvasRenderer<T> canvasToString(String nullValue) {
    final CanvasRenderable nullPresentation = text(nullValue);
    return new CanvasToStringRenderer<T>(nullPresentation);
  }

  public static <T extends CanvasRenderable> CanvasRenderer<T> canvasDefault(final String nullValue) {
    CanvasRenderable nullPresentation = text(nullValue);
    return new DefaultCanvasRenderer<T>(nullPresentation);
  }

  @NotNull
  public static <T extends CanvasRenderable, C extends Collection<T>> CanvasRenderer<C>
    canvasDefaultCollection(final String nullValue, final String separator)
  {
    return new CanvasCollectionRenderer<T, C>(nullValue, separator);
  }

  public static <T extends CanvasRenderable> CanvasRenderer<List<T>>
    canvasDefaultList(final String nullValue, final String separator) 
  {
    return Renderers.<T, List<T>>canvasDefaultCollection(nullValue, separator);
  }

  public static <T extends CanvasRenderable> CanvasRenderer<Set<T>>
    canvasDefaultSet(final String nullValue, final String separator)
  {
    return Renderers.<T, Set<T>>canvasDefaultCollection(nullValue, separator);
  }

  public static CanvasRenderer<Date> shortestDate() {
    return new ShortestDateRenderer();
  }

  public static <E, V> CollectionRenderer<E> convertingRenderer(final CollectionRenderer<? super V> renderer,
    final Convertor<? super E, V> convertor)
  {
    return new ConvertingRenderer<E, V>(renderer, convertor);
  }

  public static <E, V> CanvasRenderer<E> convertingCanvasRenderer(final CanvasRenderer<? super V> renderer,
    final Convertor<E, V> convertor)
  {
    return new ConvertingCanvasRenderer<E, V>(renderer, convertor);
  }

  public static <T> CanvasRenderer<T> constText(final String value) {
    return new ConstTextRenderer<T>(value);
  }

  public static <T, C extends Collection<T>> CanvasRenderer<C> listRenderer(final CanvasRenderer<T> itemRenderer,
    final CanvasRenderer<T> separator)
  {
    return new ListRenderer<T, C>(separator, itemRenderer);
  }

  public static <T, W extends ObjectWrapper<T>> CanvasRenderer<W> createUserObjectCanvasRenderer(
    CanvasRenderer<? super T> renderer)
  {
    return new WrappedObjectRenderer<T, W>(renderer);
  }

  public static <T, W extends ObjectWrapper<T>> CollectionRenderer<W> createUserObjectRenderer(
    CanvasRenderer<? super T> renderer)
  {
    return createRenderer(Renderers.<T, W>createUserObjectCanvasRenderer(renderer));
  }

  public static CanvasRenderer<Icon> iconRenderer() {
    return new CanvasRenderer<Icon>() {
      public void renderStateOn(CellState state, Canvas canvas, Icon icon) {
        if (icon != null)
          canvas.setIcon(icon);
      }
    };
  }

  public static <T> CanvasRenderer<T> textCanvasRenderer(final Convertor<T, String> textConvertor) {
    return new CanvasRenderer<T>() {
      @Override
      public void renderStateOn(CellState state, Canvas canvas, T item) {
        canvas.appendText(Util.NN(textConvertor.convert(item)));
      }
    };
  }

  public static class DefaultCanvasRenderer<T> implements CanvasRenderer<T> {
    private final CanvasRenderable myNullPresentation;

    public DefaultCanvasRenderer(CanvasRenderable nullPresentation) {
      myNullPresentation = nullPresentation;
    }

    public void renderStateOn(CellState state, Canvas canvas, T item) {
      CanvasRenderable renderable;
      if (item != null) {
        renderable = UIUtil.getImplementor(item, CanvasRenderable.class);
      } else {
        renderable = myNullPresentation;
      }
      if (renderable == null) {
        canvas.appendText(String.valueOf(item));
      } else {
        renderable.renderOn(canvas, state);
      }
    }
  }


  public static class DefaultCollectionRenderer<T> implements CollectionRenderer<T> {
    private final DefaultCanvasComponent myCanvasComponent;
    private final CanvasRenderer<T> myRenderer;

    public DefaultCollectionRenderer(CanvasRenderer<T> renderer) {
      myRenderer = renderer;
      myCanvasComponent = new DefaultCanvasComponent();
    }

    public JComponent getRendererComponent(CellState state, T item) {
      Canvas canvas = myCanvasComponent.prepareCanvas(state);
      myRenderer.renderStateOn(state, canvas, item);
      return myCanvasComponent;
    }

    public CanvasRenderer<T> getRenderer() {
      return myRenderer;
    }
  }


  private static class ToStringRenderer<T> implements CanvasRenderer<T> {
    public void renderStateOn(CellState state, Canvas canvas, T item) {
      canvas.appendText(item != null ? item.toString() : "");
    }
  }


  private static class TextRenderable implements CanvasRenderable {
    private final String myText;

    public TextRenderable(String text) {
      myText = text;
    }

    public void renderOn(Canvas canvas, CellState state) {
      canvas.appendText(myText);
    }
  }


  private static class CanvasToStringRenderer<T> implements CanvasRenderer<T> {
    private final CanvasRenderable myNullPresentation;

    public CanvasToStringRenderer(CanvasRenderable nullPresentation) {
      myNullPresentation = nullPresentation;
    }

    public void renderStateOn(CellState state, Canvas canvas, T item) {
      if (item == null)
        myNullPresentation.renderOn(canvas, state);
      else
        canvas.appendText(item.toString());
    }
  }


  private static class CanvasCollectionRenderer<T extends CanvasRenderable, C extends Collection<T>> implements CanvasRenderer<C> {
    private final String myNullValue;
    private final String mySeparator;

    public CanvasCollectionRenderer(String nullValue, String separator) {
      myNullValue = nullValue;
      mySeparator = separator;
    }

    @Override
    public void renderStateOn(CellState state, Canvas canvas, C item) {
      if (item == null) {
        canvas.appendText(myNullValue);
        return;
      }
      String sep = "";
      for (T element : item) {
        canvas.appendText(sep);
        element.renderOn(canvas, state);
        sep = mySeparator;
      }
    }
  }


  private static class ShortestDateRenderer implements CanvasRenderer<Date> {
    public void renderStateOn(CellState state, Canvas canvas, Date item) {
      if (item == null || item.getTime() < Const.DAY)
        return;
      canvas.appendText(DateUtil.toLocalDateOrTime(item));
      canvas.setToolTipText(DateUtil.toLocalDateTime(item));
    }
  }


  public static class ConvertingRenderer<E, V> implements CollectionRenderer<E> {
    private final CollectionRenderer<? super V> myRenderer;
    private final Convertor<? super E, V> myConvertor;

    public ConvertingRenderer(CollectionRenderer<? super V> renderer, Convertor<? super E, V> convertor) {
      myRenderer = renderer;
      myConvertor = convertor;
    }

    public JComponent getRendererComponent(CellState state, E item) {
      return myRenderer.getRendererComponent(state, myConvertor.convert(item));
    }
  }


  private static class ConvertingCanvasRenderer<E, V> implements CanvasRenderer<E> {
    private final CanvasRenderer<? super V> myRenderer;
    private final Convertor<E, V> myConvertor;

    public ConvertingCanvasRenderer(CanvasRenderer<? super V> renderer, Convertor<E, V> convertor) {
      myRenderer = renderer;
      myConvertor = convertor;
    }

    public void renderStateOn(CellState state, Canvas canvas, E e) {
      myRenderer.renderStateOn(state, canvas, myConvertor.convert(e));
    }
  }


  private static class ConstTextRenderer<T> implements CanvasRenderer<T> {
    private final String myValue;

    public ConstTextRenderer(String value) {
      myValue = value;
    }

    public void renderStateOn(CellState state, Canvas canvas, T item) {
      canvas.appendText(myValue);
    }
  }


  private static class ListRenderer<T, C extends Collection<T>> implements CanvasRenderer<C> {
    private final CanvasRenderer<T> mySeparator;
    private final CanvasRenderer<T> myItemRenderer;

    public ListRenderer(CanvasRenderer<T> separator, CanvasRenderer<T> itemRenderer) {
      mySeparator = separator;
      myItemRenderer = itemRenderer;
    }

    public void renderStateOn(CellState state, Canvas canvas, C collection) {
      if (collection == null)
        return;
      boolean first = true;
      for (T item : collection) {
        if (!first)
          mySeparator.renderStateOn(state, canvas, item);
        first = false;
        myItemRenderer.renderStateOn(state, canvas, item);
      }
    }
  }


  private static class WrappedObjectRenderer<T, W extends ObjectWrapper<? extends T>> implements CanvasRenderer<W> {
    private final CanvasRenderer<? super T> myRenderer;

    public WrappedObjectRenderer(CanvasRenderer<? super T> renderer) {
      myRenderer = renderer;
    }

    public void renderStateOn(CellState state, Canvas canvas, W item) {
      myRenderer.renderStateOn(state, canvas, item != null ? item.getUserObject() : null);
    }
  }
}
