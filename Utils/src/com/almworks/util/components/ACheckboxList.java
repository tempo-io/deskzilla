package com.almworks.util.components;

import com.almworks.util.advmodel.*;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.collections.IntArray;
import com.almworks.util.components.plaf.macosx.Aqua;
import com.almworks.util.components.renderer.ListCanvasWrapper;
import com.almworks.util.ui.MegaMouseAdapter;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.detach.Detach;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ACheckboxList<T> extends BaseAList<T> {
  private static final int MODIFIERS = InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK | InputEvent.ALT_MASK | InputEvent.META_MASK;

  private final CheckboxRenderer<T> myRenderer = new CheckboxRenderer<T>();
  private final SelectionAccessor<T> myChecked;
  private final ListCanvasWrapper<T> myCanvasWrapper = new ListCanvasWrapper<T>(myRenderer) {
    @Override
    public Component getListCellRendererComponent(
      JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
      Component result = prepareRendererComponent(list, value, index, isSelected, cellHasFocus, true);
      myRenderer.setEnabled(list.isEnabled());
      myRenderer.setChecked(myChecked.isSelectedAt(index));
      return result;
    }

    @Override
    protected Component prepareRendererComponent(JList list, Object value, int index, boolean isSelected,
      boolean cellHasFocus, boolean useBgRenderer)
    {
      final Component c = super.prepareRendererComponent(list, value, index, isSelected, cellHasFocus, useBgRenderer);
      myRenderer.transferCanvasDecorationsToBackground();
      return c;
    }
  };

  private boolean mySettingModel = false;

  public ACheckboxList() {
    this(new ListModelHolder<T>());
  }

  public ACheckboxList(AListModel<? extends T> model) {
    this(new ListModelHolder<T>(model));
  }

  private ACheckboxList(ListModelHolder<T> listModelHolder) {
    super(listModelHolder);
    final JList list = getScrollable();
    myCanvasWrapper.setLafRenderer(list.getCellRenderer());
    DefaultListSelectionModel checkedModel = new DefaultListSelectionModel();
    checkedModel.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        JComponent jList = getSwingComponent();
        if (jList == null)
          return;

        int index0 = e.getFirstIndex();
        Rectangle rect1 = getCellBounds(index0);
        if (rect1 == null) {
          jList.repaint();
          return;
        }
        int index1 = e.getLastIndex();
        if (index1 == index0) {
          jList.repaint(rect1);
          return;
        }
        Rectangle rect2 = getCellBounds(index1);
        if (rect2 == null) {
          jList.repaint();
          return;
        }
        jList.repaint(0, rect1.y, jList.getWidth(), rect2.y - rect1.y + rect2.height);
      }
    });
    myChecked = new ListSelectionAccessor<T>(checkedModel, getModelHolder());
    ListSelectionModelAdapter.createListening(getModelHolder(), checkedModel, true);
    list.setCellRenderer(myCanvasWrapper);
    new LifeCheckboxController().install(this);
    KeyStroke toggleStroke = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false);
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(toggleStroke, "toggle");
    getActionMap().put("toggle", new ToggleAction());
    list.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(toggleStroke, "---");
    list.getInputMap(WHEN_FOCUSED).put(toggleStroke, "---");
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if(e.getButton() != MouseEvent.BUTTON1 || (e.getModifiers() & MODIFIERS) != 0) {
          return;
        }
        
        if(list.isEnabled()) {
          final int index = getElementIndexAt(e.getX(), e.getY());
          if(index >= 0 && index < getCollectionModel().getSize()) {
            if(myChecked.isSelectedAt(index)) {
              myChecked.removeSelectionAt(index);
            } else {
              myChecked.addSelectionIndex(index);
            }
          }
        }
      }
    });
  }

  public void updateUI() {
    boolean tmpRemove = myCanvasWrapper.beforeUpdateUI(getScrollable());
    getScrollable().updateUI();
    super.updateUI();
    myCanvasWrapper.afterUpdateUI(getScrollable(), tmpRemove);
  }

  public void setCanvasRenderer(CanvasRenderer<? super T> renderer) {
    boolean rendererChanged = myCanvasWrapper.setCanvasRenderer(renderer);
    if (rendererChanged && getModelHolder().getSize() > 0) {
      invalidate();
      revalidate();
      repaint();
    }
  }

  public CanvasRenderer<? super T> getCanvasRenderer() {
    return myCanvasWrapper.getCanvasRenderer();
  }

  public SelectionAccessor<T> getCheckedAccessor() {
    return myChecked;
  }

  public Detach setCollectionModel(AListModel<? extends T> model, boolean keepSelection) {
    java.util.List<T> selected = null;
    if(keepSelection) {
      selected = myChecked.getSelectedItems();
      myChecked.setEventsInhibited(true);
    }

    mySettingModel = true;
    try {
      return super.setCollectionModel(model, keepSelection);
    } finally {
      mySettingModel = false;
      if(keepSelection) {
        myChecked.setSelected(selected);
        myChecked.setEventsInhibited(false);
        myChecked.fireSelectionChanged();
        if(myChecked.getSelectedCount() > 0) {
          myChecked.fireSelectedItemsChanged();
          scrollSelectionToView();
        }
      }
    }
  }

  @Override
  public void scrollSelectionToView() {
    if(mySettingModel) {
      return;
    }
    
    final int index = getCheckedAccessor().getSelectedIndex();
    if(index >= 0) {
       UIUtil.ensureRectVisiblePartially(this, getScrollable().getCellBounds(index, index));
    }
  }

  public void scrollListSelectionToView() {
    super.scrollSelectionToView();
  }

  // In some LAFs (Windows 6.0+, maybe some other) checkbox selection is animated, which causes "flickering"
  // when moving mouse from checked checkbox to unchecked (tick appears for a split second and then disappears gradually)
  // To eliminate this flickring, two live checkboxes are used, one selected and another not selected, when hovering on
  // the row the appropriate one is shown
  private class LifeCheckboxController extends MegaMouseAdapter
    implements ItemListener, ListDataListener, ChangeListener, PropertyChangeListener
  {
    /**
     * Invariant: {@code mySourceCheckBox[0].isSelected() ^ mySourceCheckBox[1].isSelected() }
     */
    private final JCheckBox[] mySourceCheckBox = new JCheckBox[] { new JCheckBox(), new JCheckBox() };
    /**
     * Index of the checkbox that is in selected state.
     */
    private int mySelectedCheckBox;
    /**
     * One of source check boxes.
     */
    private JCheckBox myVisibleCheckBox;
    private Rectangle myPrevButtonRect = null;
    private int myLastIndex = -1;
    private boolean myMovingButton = false;

    private LifeCheckboxController() {
      myVisibleCheckBox = mySourceCheckBox[0];
    }

    public void mouseMoved(MouseEvent e) {
      int index = getElementIndexAt(e.getX(), e.getY());
      if (index < 0 || index >= getCollectionModel().getSize()) {
        removeButton();
        return;
      }
      if (index == myLastIndex)
        return;
      showLiveAt(index);
    }

    public void itemStateChanged(ItemEvent e) {
      if (myMovingButton)
        return;
      if (e.getItem() != myVisibleCheckBox) {
        return;
      }
      assert myVisibleCheckBox.isVisible();
      int lastIndex = myLastIndex;
      if (lastIndex >= 0 && lastIndex < getCollectionModel().getSize()) {
        changeSelected();
        if (myVisibleCheckBox.isSelected()) {
          myChecked.addSelectionIndex(lastIndex);
        } else {
          myChecked.removeSelectionAt(lastIndex);
        }
        getSelectionAccessor().setSelectedIndex(lastIndex);
      }
    }

    private void changeSelected() {
      mySelectedCheckBox = 1 - mySelectedCheckBox;
      setSelected(mySelectedCheckBox, true);
      setSelected(1 - mySelectedCheckBox, false);
    }

    private void setSelected(int si, boolean selected) {
      JCheckBox jcb = mySourceCheckBox[si];
      // cannot change selected state of myVisibleCheckBox because we're already processing the event of changing its state
      if (jcb != myVisibleCheckBox) {
        jcb.setSelected(selected);
      }
    }

    public void intervalAdded(ListDataEvent e) {
      if (!affectsLiveComponent(e))
        return;
      assert myLastIndex < getCollectionModel().getSize();
      showLiveAt(myLastIndex);
    }

    private boolean affectsLiveComponent(ListDataEvent e) {
      if (myLastIndex < 0)
        return false;
      if (myLastIndex < Math.min(e.getIndex0(), e.getIndex1()))
        return false;
      return true;
    }

    public void intervalRemoved(ListDataEvent e) {
      if (!affectsLiveComponent(e))
        return;
      if (myLastIndex >= getCollectionModel().getSize())
        removeButton();
      else
        showLiveAt(myLastIndex);
    }

    public void contentsChanged(ListDataEvent e) {
    }

    public void propertyChange(PropertyChangeEvent evt) {
      if(evt.getSource() instanceof JList
        && "enabled".equals(evt.getPropertyName())
        && evt.getNewValue() instanceof Boolean)
      {
        Boolean enabled = (Boolean) evt.getNewValue();
        mySourceCheckBox[0].setEnabled(enabled);
        mySourceCheckBox[1].setEnabled(enabled);
      }
    }

    private void showLiveAt(int index) {
      assert !myMovingButton;
      myMovingButton = true;
      try {
        final Rectangle rect = getCellBounds(index);
        prepareCellRenderer(index);
        myRenderer.getClientCellRect(rect);
        myRenderer.getCheckboxLocation(rect);
        showSelected(myChecked.isSelectedAt(index));
        // updateUI() is called to prevent animation. (see PLO-386)
        myVisibleCheckBox.updateUI();
        myLastIndex = index;
        myPrevButtonRect = rect;
        if (!AwtUtil.setBounds(mySourceCheckBox[0], rect)) {
          return;
        }
        if (!AwtUtil.setBounds(mySourceCheckBox[1], rect)) {
          return;
        }
        myVisibleCheckBox.revalidate();
        myVisibleCheckBox.repaint();
      } finally {
        myMovingButton = false;
      }
    }

    private void prepareCellRenderer(int index) {
      final JListAdapter list = getScrollable();
      final Object item = getCollectionModel().getAt(index);
      final boolean isSelected = getSelectionAccessor().isSelectedAt(index);
      final boolean hasFocus = list.getSelectionModel().getLeadSelectionIndex() == index;
      myCanvasWrapper.getListCellRendererComponent(list, item, index, isSelected, hasFocus);
    }

    private void showSelected(boolean selected) {
      int si = getSourceIndex(selected);
      if (myVisibleCheckBox != mySourceCheckBox[si]) {
        myVisibleCheckBox.setVisible(false);
        myVisibleCheckBox = mySourceCheckBox[si];
      }
      myVisibleCheckBox.setVisible(true);
    }

    private void removeButton() {
      myVisibleCheckBox.setVisible(false);
      myLastIndex = -1;
      if (myPrevButtonRect != null)
        getSwingComponent().repaint(myPrevButtonRect);
      myPrevButtonRect = null;
    }

    public void install(ACheckboxList<?> list) {
      mySelectedCheckBox = 0;
      setupSourceCheckbox(true);
      setupSourceCheckbox(false);
      JList jList = list.getScrollable();
      jList.addMouseMotionListener(this);
      jList.add(mySourceCheckBox[0]);
      jList.add(mySourceCheckBox[1]);
      jList.getModel().addListDataListener(this);
      jList.addPropertyChangeListener(this);
      myChecked.addAWTChangeListener(this);
    }

    private void setupSourceCheckbox(boolean selected) {
      JCheckBox chb = mySourceCheckBox[getSourceIndex(selected)];
      chb.setFocusable(false);
      chb.addMouseListener(this);
      chb.addItemListener(this);
      chb.setOpaque(false);
      chb.setBorderPaintedFlat(true);
      Aqua.makeSmallComponent(chb);
      chb.setSelected(selected);
    }

    private int getSourceIndex(boolean selected) {
      return selected ? mySelectedCheckBox : 1 - mySelectedCheckBox;
    }

    public void onChange() {
      if (myLastIndex >= 0) {
        showSelected(myChecked.isSelectedAt(myLastIndex));
      }
    }

    public void mouseClicked(MouseEvent e) {
      if(myVisibleCheckBox.isEnabled()) {
        requestFocusInWindow();
      }
    }
  }

  private class ToggleAction extends AbstractAction {
    public void actionPerformed(ActionEvent e) {
      SelectionAccessor<T> selection = getSelectionAccessor();
      SelectionAccessor<T> checked = getCheckedAccessor();
      int[] idx = selection.getSelectedIndexes();
      if (idx.length == 0) return;
      boolean select = checked.isSelectedAt(idx[0]);
      checked.updateSelectionAt(IntArray.create(idx), !select);
    }
  }
}
