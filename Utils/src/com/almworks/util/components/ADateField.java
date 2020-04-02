package com.almworks.util.components;

import com.almworks.util.L;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.events.FireEventSupport;
import com.almworks.util.model.ValueModel;
import com.almworks.util.ui.*;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.Const;
import org.almworks.util.detach.*;
import org.freixas.jcalendar.*;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.Document;
import javax.swing.undo.CompoundEdit;
import java.awt.*;
import java.text.DateFormat;
import java.util.*;

import static com.almworks.util.ui.SingleChildLayout.CONTAINER;
import static com.almworks.util.ui.SingleChildLayout.PREFERRED;

/**
 * @author dyoma
 */
public class ADateField extends JComponent implements UndoUtil.Editable {
  private final FieldWithMoreButton<JTextField> myField = new FieldWithMoreButton<JTextField>();
  private final DateFormat myFormat;
  private final FireEventSupport<UndoableEditListener> myUndoListeners =
    FireEventSupport.create(UndoableEditListener.class);
  private final Lifecycle mySwingLife = new Lifecycle(false);
  private final TimeZone myTimezone;

  private CompoundEdit myCompoundEdit = null;
  private ValueModel<Date> myDateModel = ValueModel.create();

  public ADateField() {
    this(DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()), TimeZone.getDefault());
  }

  public ADateField(DateFormat format, TimeZone timeZone) {
    myFormat = format;
    myTimezone = timeZone;
    myFormat.setTimeZone(timeZone);
    myField.setField(new JTextField());
    setLayout(new SingleChildLayout(CONTAINER, PREFERRED, CONTAINER, PREFERRED));
    add(myField);
    myField.setActionName(L.tooltip("Open Calendar"));
    myField.setAction(new MyDropDown());
    updateUI();
  }

  public void addNotify() {
    super.addNotify();
    start();
  }

  private void start() {
    if (mySwingLife.cycleStart()) {
      linkModel();
      setupUndo();
    }
  }

  public void removeNotify() {
    mySwingLife.cycleEnd();
    super.removeNotify();
  }

  private void linkModel() {
    Lifespan life = mySwingLife.lifespan();
    assert !life.isEnded() : this;
    final boolean[] updating = {true};
    final JTextField textField = myField.getField();

    final Color normalFg = textField.getForeground();
    final Color errorFg = GlobalColors.ERROR_COLOR;
    life.add(UIUtil.addTextListener(textField, new ChangeListener() {
      public void onChange() {
        if (updating[0])
          return;
        updating[0] = true;
        try {
          boolean error = false;
          boolean hasDate = true;
          String text = textField.getText().trim();
          Date date = null;
          if (text.length() > 0) {
            myFormat.setLenient(false);
            try {
              myFormat.parse(text);
            } catch (Exception e) {
              error = true;
            }
            myFormat.setLenient(true);
            try {
              date = myFormat.parse(text);
            } catch (Exception e) {
              // ignore
              hasDate = false;
            }
          }
          textField.setForeground(error ? errorFg : normalFg);
          if (hasDate) {
            myDateModel.setValue(date);
          }
        } finally {
          updating[0] = false;
        }
      }
    }));
    ChangeListener modelListener = new ChangeListener() {
      public void onChange() {
        if (updating[0])
          return;
        updating[0] = true;
        try {
          Date value = myDateModel.getValue();
          String text = value == null ? "" : myFormat.format(value);
          String oldText = myField.getField().getText().trim();
          if (!oldText.equals(text)) {
            UIUtil.setTextKeepView(myField.getField(), text);
          }
        } finally {
          updating[0] = false;
        }
      }
    };
    myDateModel.addChangeListener(life, modelListener);
    updating[0] = false;
    modelListener.onChange();
  }

  private void setupUndo() {
    final Document document = myField.getField().getDocument();
    final UndoableEditListener listener = new UndoableEditListener() {
      public void undoableEditHappened(UndoableEditEvent e) {
        if (myCompoundEdit == null)
          myUndoListeners.getDispatcher().undoableEditHappened(e);
        else
          myCompoundEdit.addEdit(e.getEdit());
      }
    };
    document.addUndoableEditListener(listener);
    UndoUtil.removeUndoSupport(myField.getField());
    UndoUtil.CUSTOM_EDITABLE.putClientValue(myField.getField(), this);
    UndoUtil.addUndoSupport(myField.getField());
    mySwingLife.lifespan().add(new Detach() {
      protected void doDetach() throws Exception {
        document.removeUndoableEditListener(listener);
        UndoUtil.addUndoSupport(myField.getField());
      }
    });
  }

  public void updateUI() {
    super.updateUI();
    JComponent field = myField.getField();
    Dimension prefSize = field.getPreferredSize();
    int sampleWidth = field.getFontMetrics(field.getFont()).stringWidth(myFormat.format(new Date()));
    field.setPreferredSize(new Dimension(sampleWidth * 5 / 4 + AwtUtil.getInsetWidth(field), prefSize.height));
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myField.setEnabled(enabled);
  }

  public void addUndoableEditListener(UndoableEditListener listener) {
    myUndoListeners.addStraightListener(listener);
  }

  public void removeUndoableEditListener(UndoableEditListener listener) {
    myUndoListeners.removeListener(listener);
  }

  public int getColumns() {
    return myField.getField().getColumns();
  }

  public void setColumns(int columns) {
    myField.getField().setColumns(columns);
  }

  public void setDate(Date date) {
//    String text = date == null ? "" : myFormat.format(date);
    assert myCompoundEdit == null;
    myCompoundEdit = new CompoundEdit();
    myDateModel.setValue(date);
//    UIUtil.setFieldText(myField.getField(), text);
    myCompoundEdit.end();
    myUndoListeners.getDispatcher()
      .undoableEditHappened(new UndoableEditEvent(myField.getField().getDocument(), myCompoundEdit));
    myCompoundEdit = null;
  }

  public void setDateOnly(Date date) {
    if (date == null) {
      myDateModel.setValue(null);
      return;
    }
    Date oldval = myDateModel.getValue();
    long keep = 0;
    if (oldval != null) {
      long t = oldval.getTime();
      t += myTimezone.getOffset(t);
      keep = t % Const.DAY;
    }
    long newval = date.getTime();
    newval += myTimezone.getOffset(newval);
    newval -= newval % Const.DAY;
    newval += keep;

    // guess new gmt
    int diff1 = myTimezone.getOffset(newval);
    long newval1 = newval - diff1;
    int diff2 = myTimezone.getOffset(newval1);
    newval = diff1 == diff2 ? newval1 : newval - diff2;

    myDateModel.setValue(new Date(newval));
  }

  public DateFormat getFormat() {
    return myFormat;
  }

  public ValueModel<Date> getDateModel() {
    return myDateModel;
  }

  public void setDateModel(ValueModel<Date> model) {
    if (model == null)
      throw new NullPointerException();
    myDateModel = model;
    if (mySwingLife.cycleEnd()) {
      start();
    }
  }

  public JComponent getDefaultFocusComponent() {
    return myField.getField();
  }

  private class MyDropDown extends DropDownListener.ForComponent {
    public MyDropDown() {
      super(ADateField.this.myField);
    }

    protected JComponent createPopupComponent() {
      Date date = getDateModel().getValue();
      JCalendar component = new JCalendar();
      component.setBackground(UIManager.getColor("TextField.background"));
      component.setBorder(UIManager.getBorder("PopupMenu.border"));
      if (date != null) {
        component.setDate(date);
      } else {
        component.setNullAllowed(true);
        component.setDate(null);
        component.setNullAllowed(false);
      }
      component.addDateListener(new DateListener() {
        public void dateChanged(DateEvent e) {
          Date date = null;
          if (e != null) {
            Calendar selectedDate = e.getSelectedDate();
            if (selectedDate != null) {
              date = selectedDate.getTime();
            }
          }
          setDateOnly(date);
          hideDropDown();
        }
      });
      return component;
    }

    protected void onDropDownHidden() {
      UIUtil.requestFocusLater(myField.getField());
    }
  }
}
