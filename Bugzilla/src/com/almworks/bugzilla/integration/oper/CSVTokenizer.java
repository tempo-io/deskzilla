package com.almworks.bugzilla.integration.oper;

import org.almworks.util.Log;
import org.almworks.util.Util;

public class CSVTokenizer {
  private final String myCsv;
  private final boolean myIgnoreCR;

  public CSVTokenizer(String csv) {
    this(csv, false);
  }
  
  public CSVTokenizer(String csv, boolean ignoreCR) {
    myIgnoreCR = ignoreCR;
    myCsv = Util.NN(csv);
  }

  public boolean parse(CSVVisitor visitor) {
    int len = myCsv.length();
    StringBuilder cell = new StringBuilder();
    int row = 0;
    int col = 0;
    char quoteChar = 0;
    for (int i = 0; i <= len; i++) {
      char c = i == len ? 0 : myCsv.charAt(i);
      if (myIgnoreCR && c == '\r') {
        // ignore
      } else if (c == '\n' || c == '\r' || c == 0) {
        if (!visitor.visitCell(row, col, cell.toString()))
          return false;
        if (c == '\r' && i + 1 < len && myCsv.charAt(i + 1) == '\n')
          i++;
        if (!visitor.visitRow(row))
          return false;
        row++;
        col = 0;
        quoteChar = 0;
        cell.setLength(0);
      } else if (isQuote(c)) {
        if (quoteChar == 0) {
          if (cell.length() == 0) {
            quoteChar = c;
          } else {
            cell.append(c);
          }
        } else {
          char nc = i + 1 < len ? myCsv.charAt(i + 1) : 0;
          if (nc == c) {
            cell.append(c);
            i++;
          } else if (isDelim(nc) || nc == '\r' || nc == '\n' || nc == 0) {
            quoteChar = 0;
          } else {
            Log.debug("warning parsing CSV: [" + c + "] found inside a cell");
            cell.append(c);
          }
        }
      } else if (isDelim(c)) {
        if (!visitor.visitCell(row, col, cell.toString()))
          return false;
        col++;
        cell.setLength(0);
        quoteChar = 0;
      } else {
        cell.append(c);
      }
    }
    return true;
  }

  private static boolean isDelim(char c) {
    return c == ',' || c == ';';
  }

  private static boolean isQuote(char c) {
    return c == '\"' || c == '\'';
  }

  public static interface CSVVisitor {
    /**
     * Called after a cell is parsed
     *
     * @param row, 0-based
     * @param col, 0-based
     * @param cell value
     * @return if false, interrupt parsing
     */
    boolean visitCell(int row, int col, String cell);

    /**
     * Called after a row has been fully parsed
     *
     * @param row number
     * @return if false, interrupt parsing
     */
    boolean visitRow(int row);
  }
}
