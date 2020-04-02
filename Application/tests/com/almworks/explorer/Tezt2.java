package com.almworks.explorer;

import javax.print.PrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.print.*;


public class Tezt2 extends JComponent {
  public static void main(String[] args) {

    PrinterJob job = PrinterJob.getPrinterJob();
    PrintService[] services = PrinterJob.lookupPrintServices();
    boolean b = job.printDialog();
    PageFormat pageFormat = job.pageDialog(new PageFormat());
    //PageFormat pageFormat = new PageFormat();
    job.setJobName("x");
    job.setCopies(2);
    //PageFormat pageFormat = new PageFormat();
    job.setPrintable(new Printable() {
      public int print(Graphics g, PageFormat pageFormat, int pageIndex) throws PrinterException {

        Paper paper = pageFormat.getPaper();
        g.drawString("dsfsafsadfsadf", 100, 100);
        g.setColor(Color.WHITE);
        Rectangle bounds = g.getClipBounds();
        g.fillRect(0, 0, bounds.width, bounds.height);
        g.setColor(new Color(180, 180, 180));
        for (int i = 1; i < bounds.width; i += 2) {
          g.drawLine(i, 0, i, bounds.height);
        }

        g.setColor(Color.BLACK);
        g.fillRect(10, 10, 5, 5);
        return pageIndex > 0 ? NO_SUCH_PAGE : PAGE_EXISTS;
      }
    }, pageFormat);
    try {
      job.print();
      int a = 0;
    } catch (PrinterException e) {
      e.printStackTrace();  
    }
  }

  protected void paintComponent(Graphics g) {
    Dimension size = getSize();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, size.width, size.height);
    g.setColor(new Color(180, 180, 180));
    for (int i = 1; i < size.width; i += 2) {
      g.drawLine(i, 0, i, size.height);
    }

    g.setColor(Color.BLACK);
    g.fillRect(10, 10, 5, 5);
  }
}
