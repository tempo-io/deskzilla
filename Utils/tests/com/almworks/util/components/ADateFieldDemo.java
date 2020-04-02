package com.almworks.util.components;

import com.almworks.util.collections.ChangeListener;
import com.almworks.util.debug.DebugFrame;
import org.almworks.util.detach.Lifespan;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.util.*;

public class ADateFieldDemo {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        JPanel panel = new JPanel(new FlowLayout());
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US);
        TimeZone timeZone = TimeZone.getTimeZone("America/Chicago");
        final ADateField field = new ADateField(format, timeZone);
        field.setDate(new Date());
        final JTextField shower = new JTextField(20);
        field.getDateModel().addChangeListener(Lifespan.FOREVER, new ChangeListener() {
          public void onChange() {
            shower.setText(String.valueOf(field.getDateModel().getValue()));
          }
        });
        panel.add(field);
        panel.add(shower);
        DebugFrame.show(panel, 500, 200, true);
      }
    });
  }
}
