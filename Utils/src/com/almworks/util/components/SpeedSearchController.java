package com.almworks.util.components;

import com.almworks.util.advmodel.AListModel;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.ui.ComponentProperty;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.swing.SwingTreeUtil;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SpeedSearchController<T> extends KeyAdapter {
  private static final ComponentProperty<SpeedSearchController<?>> CONTROLLER = ComponentProperty.createProperty("controller");
  private final FlatCollectionComponent<T> myList;
  private final SpeedSearchProvider<T> myProvider;
  private boolean myCaseSensitive = false;
  private boolean mySearchSubstring = false;
  private boolean myIgnoreSpace = false;
  private SearchPopup mySearchPopup;

  private SpeedSearchController(FlatCollectionComponent<T> aList, SpeedSearchProvider<T> provider) {
    myList = aList;
    myProvider = provider;
  }

  public void setCaseSensitive(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
  }

  public void setIgnoreSpace(boolean ignoreSpace) {
    myIgnoreSpace = ignoreSpace;
  }

  public void setSearchSubstring(boolean searchSubstring) {
    mySearchSubstring = searchSubstring;
  }

  public boolean isSpeedSearchFocused() {
    return mySearchPopup != null && mySearchPopup.isFocused();
  }

  @Override
  public void keyTyped(KeyEvent e) {
    if (mySearchPopup != null) {
      mySearchPopup.hidePopup(false);
      Log.warn("SSC: no search popup");
    }
    int modifiers = e.getModifiersEx();
    if ((modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0) return;
    if (!myProvider.isApplicableTo(myList) || myList.getCollectionModel().getSize() == 0) return;
    char letter = e.getKeyChar();
    int type = Character.getType(letter);
    if (type == Character.CONTROL) return;
    if (myIgnoreSpace && Character.isSpaceChar(letter)) return;
    mySearchPopup = SearchPopup.open(myList, 0, 0, this);
    mySearchPopup.addLetter(letter);
  }

  @NotNull
  public static <T> SpeedSearchController<T> install(BaseAList<T> list) {
    SpeedSearchController<T> controller = (SpeedSearchController<T>) CONTROLLER.getClientValue(list);
    if (controller != null) return controller;
    controller = new SpeedSearchController<T>(list, new ListCanvasSpeedSearchProvider<T>());
    if (list.getCanvasRenderer() == null) {
      assert false; // Current implementation won't work without CanvasRenderer. Install speed-search after CanvasRenderer is provided.
      return controller;
    }
    CONTROLLER.putClientValue(list, controller);
    list.getSwingComponent().addKeyListener(controller);
    list.setSwingSpeedSearchEnabled(false);
    if (list instanceof ACheckboxList) controller.setIgnoreSpace(true);
    return controller;
  }

  public static <T> SpeedSearchController<T> install(ATable<T> table, int column, CanvasRenderer<T> renderer) {
    SpeedSearchController<T> controller = (SpeedSearchController<T>) CONTROLLER.getClientValue(table);
    if (controller != null) return controller;
    controller = new SpeedSearchController<T>(table, new TableColumnSearchProvider<T>(renderer, column));
    CONTROLLER.putClientValue(table, controller);
    table.getSwingComponent().addKeyListener(controller);
    return controller;
  }

  @Nullable
  public static SpeedSearchController getInstalled(Component component) {
    if (component instanceof JComponent) {
      SpeedSearchController<?> controller = CONTROLLER.getClientValue((JComponent) component);
      if (controller != null) return controller;
    }
    FlatCollectionComponent collectionComponent =
      SwingTreeUtil.findAncestorOfType(component, FlatCollectionComponent.class);
    if (collectionComponent == null) return null;
    JComponent jComponent = collectionComponent.toComponent();
    return jComponent != null ? CONTROLLER.getClientValue(jComponent) : null;
  }

  public void speedSearchClosed() {
    mySearchPopup = null;
  }

  public boolean searchText(String search, int direction) {
    if (direction < -1) direction = -1;
    else if (direction > 1) direction = 1;
    SelectionAccessor<?> accessor = myList.getSelectionAccessor();
    AListModel<? extends T> model = myList.getCollectionModel();
    if (model.getSize() == 0) return false;
    if (!myCaseSensitive) search = Util.lower(search);
    int index = accessor.getSelectedIndex();
    if (index < 0) index = direction >= 0 ? 0 : model.getSize() - 1;
    if (index >= model.getSize()) index = direction >= 0 ? model.getSize() - 1 : 0;
    index += direction;
    int nextIndex = -1;
    if (direction == 0) direction = 1;
    for (int i = 0; i < model.getSize(); i++) {
      int pos = (2*model.getSize() + index + i * direction) % model.getSize();
      T item = model.getAt(pos);
      String text = myProvider.getItemText(myList, item);
      if (!myCaseSensitive) text = text.toLowerCase();
      if (matches(text, search)) {
        nextIndex = pos;
        break;
      }
    }
    if (nextIndex < 0) return false;
    accessor.setSelectedIndex(nextIndex);
    if(myList instanceof ACheckboxList) {
      // kludge: ACheckboxList.scrollSelectionToView() uses
      // checkbox selection, not list selection.
      final ACheckboxList list = (ACheckboxList) myList;
      UIUtil.ensureRectVisiblePartially(list, list.getScrollable().getCellBounds(nextIndex, nextIndex));
    } else {
      myList.scrollSelectionToView();
    }
    return true;
  }

  private boolean matches(String text, String search) {
    if (mySearchSubstring)
      return text.indexOf(search) >= 0;
    else {
      if (myIgnoreSpace) text = text.trim();
      return text.startsWith(search);
    }
  }

  public boolean isStopSearch(KeyEvent e) {
    int keyCode = e.getKeyCode();
    if (keyCode == KeyEvent.VK_ESCAPE) return true;
    if (keyCode == KeyEvent.VK_ENTER) {
      myList.getSwingComponent().dispatchEvent(e);
      return true;
    }
    if (myIgnoreSpace && keyCode == KeyEvent.VK_SPACE) {
      myList.getSwingComponent().dispatchEvent(e);
      return true;
    }
    return false;
  }

  public static boolean fixFocusedState(JComponent component, boolean cellHasFocus, int row, int column) {
    if (component.isFocusOwner() || !isFocusOwner(component)) return cellHasFocus;
    for (Container ancestor = component; ancestor != null; ancestor = ancestor.getParent()) {
      if (ancestor instanceof JList) {
        JList list = (JList) ancestor;
        return list.getLeadSelectionIndex() == row;
      } else if (ancestor instanceof JTable) {
        JTable table = (JTable) ancestor;
        return table.getSelectionModel().getLeadSelectionIndex() == row && table.getColumnModel().getSelectionModel().getLeadSelectionIndex() == column;
      } else if (ancestor instanceof JTree) {
        JTree tree = (JTree) ancestor;
        return tree.getLeadSelectionRow() == row;
      }
    }
    return cellHasFocus;
  }

  public static boolean isFocusOwner(Component component) {
    if (component.isFocusOwner()) return true;
    SpeedSearchController search = getInstalled(component);
    return search != null && search.isSpeedSearchFocused();
  }

  private static class SearchPopup implements FocusListener, KeyListener, ChangeListener {
    private static final UIUtil.Positioner POPUP_POSITIONER = Aqua.isAqua()
      ? new UIUtil.IndependentPositioner() {
          @Override
          public int getX(int screenX, int screenW, int ownerX, int ownerW, int childW) {
            return ownerX + 1;
          }
          @Override
          public int getY(int screenY, int screenH, int ownerY, int ownerH, int childH) {
            return ownerY - childH - 2;
          }
        }
      : new UIUtil.IndependentPositioner(UIUtil.ALIGN_START, UIUtil.BEFORE);

    private static final int DW = Aqua.isAqua() ? -2 : 0;

    private final JTextField myField = new JTextField();
    private final SpeedSearchController myController;
    private final Color myNormalForeground;
    private Popup myPopup;
    private JComponent myReturnFocus;
    private final DetachComposite myLife = new DetachComposite();
    private boolean mySearching;

    public SearchPopup(SpeedSearchController controller) {
      myController = controller;
      myNormalForeground = myField.getForeground();
      myField.setFocusTraversalKeysEnabled(false);
      Aqua.makeSearchField(myField);
    }

    public static SearchPopup open(FlatCollectionComponent<?> cc, int x, int y, SpeedSearchController controller) {
      final SearchPopup search = new SearchPopup(controller);

      final JTextField field = search.myField;
      if(!Aqua.isAqua()) {
        field.setBackground(UIUtil.getNoticeBackground());
      }

      JComponent component = cc.getSwingComponent();
      final JComponent visualParent = getVisualParent(component);
      final Dimension fieldSize = new Dimension(visualParent.getWidth() + DW, field.getPreferredSize().height);
      field.setPreferredSize(fieldSize);

      final Point location = UIUtil.calcPopupPosition(visualParent, POPUP_POSITIONER, fieldSize);
      search.myPopup = UIUtil.getPopup(component, field, true, location);

      UIUtil.addTextListener(field, search);
      field.addFocusListener(search);
      field.addKeyListener(search);
      search.myReturnFocus = component;

      search.myPopup.show();
      field.requestFocus();

      search.watchModel(cc);

      return search;
    }

    private void watchModel(FlatCollectionComponent<?> cc) {
      cc.getCollectionModel().addAWTChangeListener(myLife, new ChangeListener() {
        @Override
        public void onChange() {
          hidePopup(true);
        }
      });
      cc.getSelectionAccessor().addAWTChangeListener(myLife, new ChangeListener() {
        @Override
        public void onChange() {
          if (mySearching) return;
          hidePopup(true);
        }
      });
    }

    private static JComponent getVisualParent(JComponent component) {
      if(component instanceof JScrollPane) {
        return component;
      }

      Container parent = component.getParent();
      if(parent instanceof BaseAList || parent instanceof ATable || parent instanceof ATree) {
        parent = parent.getParent();
      }

      if(parent instanceof JViewport) {
        final Container granny = ((JViewport)parent).getParent();
        if(granny instanceof JScrollPane) {
          return (JComponent)granny;
        }
      }

      return component;
    }

    public void focusGained(FocusEvent e) {
      if(Aqua.isAqua()) {
        final int length = myField.getText().length();
        myField.select(length, length);
      }
    }

    public void focusLost(FocusEvent e) {
      Component c = e.getOppositeComponent();
      if(c == null || !SwingTreeUtil.isAncestor(myField, c)) hidePopup(false);
    }

    public void hidePopup(boolean returnFocus) {
      myLife.detach();
      myPopup.hide();
      myController.speedSearchClosed();
      if (returnFocus) {
        myReturnFocus.requestFocus();
      } else {
        myReturnFocus.repaint();
        myReturnFocus.requestFocusInWindow();
      }
    }

    public void addLetter(char letter) {
      myField.setText(myField.getText() + letter);
    }

    public void keyTyped(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {
      if (AwtUtil.traverseFocus(myReturnFocus, e)) {
        myReturnFocus.repaint();
        return;
      }
      if (myController.isStopSearch(e)) {
        hidePopup(true);
        return;
      }
      int keyCode = e.getKeyCode();
      if (keyCode == KeyEvent.VK_DOWN) searchNext(1);
      else if (keyCode == KeyEvent.VK_UP) searchNext(-1);
    }

    public void keyReleased(KeyEvent e) {
      AwtUtil.traverseFocus(myReturnFocus, e);
    }

    public void onChange() {
      searchNext(0);
    }

    private void searchNext(int direction) {
      String text = myField.getText();
      mySearching = true;
      try {
        boolean found = myController.searchText(text, direction);
        myField.setForeground(found ? myNormalForeground : Color.RED);
      } finally {
        mySearching = false;
      }
    }

    public boolean isFocused() {
      return SwingTreeUtil.isAncestorOfFocusOwner(myField);
    }
  }

  public interface SpeedSearchProvider<T> {
    /**
     * @return true if speedsearch is applicable to the component in current state
     */
    boolean isApplicableTo(FlatCollectionComponent<T> component);

    String getItemText(FlatCollectionComponent<T> component, T item);
  }


  private static class ListCanvasSpeedSearchProvider<T> implements SpeedSearchProvider<T> {
    private static final ListCanvasSpeedSearchProvider INSTANCE = new ListCanvasSpeedSearchProvider();
    private static final ThreadLocal<PlainTextCanvas> TEXT_CANVAS = new PlainTextCanvas.ThreadLocalFactory();

    public boolean isApplicableTo(FlatCollectionComponent<T> component) {
      return getCanvasRenderer(component) != null;
    }

    @Nullable
    private CanvasRenderer<? super T> getCanvasRenderer(FlatCollectionComponent<T> component) {
      if (!(component instanceof BaseAList)) return null;
      return ((BaseAList<T>) component).getCanvasRenderer();
    }

    public String getItemText(FlatCollectionComponent<T> component, T item) {
      CanvasRenderer<? super T> renderer = getCanvasRenderer(component);
      if (renderer == null) return "";
      return getItemText(renderer, item);
    }

    private static <T> String getItemText(CanvasRenderer<? super T> renderer, T item) {
      PlainTextCanvas textCanvas = TEXT_CANVAS.get();
      textCanvas.clear();
      renderer.renderStateOn(CellState.LABEL, textCanvas, item);
      return textCanvas.getText();
    }

    public static <T> ListCanvasSpeedSearchProvider<T> getInstance() {
      return INSTANCE;
    }
  }


  private static class TableColumnSearchProvider<T> implements SpeedSearchProvider<T> {
    private final CanvasRenderer<T> myRenderer;
    private final int myColumnIndex;

    private TableColumnSearchProvider(CanvasRenderer<T> renderer, int columnIndex) {
      myRenderer = renderer;
      myColumnIndex = columnIndex;
    }

    public boolean isApplicableTo(FlatCollectionComponent<T> component) {
      return component instanceof ATable && ((ATable) component).getColumnModel().getSize() > myColumnIndex;
    }

    public String getItemText(FlatCollectionComponent<T> component, T item) {
      return ListCanvasSpeedSearchProvider.getItemText(myRenderer, item);
    }
  }
}