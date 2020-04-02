package com.almworks.database.gui;

import com.almworks.api.inquiry.InquiryHandler;
import com.almworks.api.inquiry.InquiryKey;
import com.almworks.database.ConsistencyWrapper;
import com.almworks.util.L;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * :todoc:
 *
 * @author sereda
 */
public class InconsistencyInquiryHandler implements InquiryHandler<ConsistencyWrapper.InquiryData> {
  private final Form myForm;
  private ConsistencyWrapper.InquiryData myData;
  private Listener myListener;

  public InconsistencyInquiryHandler() {
    myForm = new Form();
    myForm.myExitButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setChoice(ConsistencyWrapper.Option.ABORT);
      }
    });
    myForm.myRetryButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setChoice(ConsistencyWrapper.Option.RETRY);
      }
    });
    myForm.myCancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setChoice(ConsistencyWrapper.Option.ERROR);
      }
    });
  }

  public InquiryKey<ConsistencyWrapper.InquiryData> getInquiryKey() {
    return ConsistencyWrapper.DATABASE_INCONSISTENT;
  }

  public void setInquiryData(ConsistencyWrapper.InquiryData inquiryData) {
    myData = inquiryData;
    myData.setChoice(null);
    updateForm();
  }

  public ConsistencyWrapper.InquiryData getInquiryData() {
    return myData;
  }

  public void setListener(Listener listener) {
    myListener = listener;
  }

  public String getInquiryTitle() {
    return L.dialog("Database Inconsistency Detected");
  }

  public JComponent getComponent() {
    return myForm.myWholePanel;
  }

  public void dispose() {
    myListener = null;
    myData = null;
  }

  private void setChoice(ConsistencyWrapper.Option choice) {
    if (myData != null) {
      myData.setChoice(choice);
      if (myListener != null)
        myListener.onAnswer(true);
    }
  }

  private void updateForm() {
    myForm.myDetails.setText(myData.getException().getMessage());
  }

  public class Form {
    private JTextArea myDetails;
    private JButton myExitButton;
    private JButton myCancelButton;
    private JButton myRetryButton;
    private JPanel myWholePanel;
  }
}
