package com.almworks.universe.data;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ExpansionInfoSink {
  /**
   * @return false to discontinue iteration
   */
  boolean visitExpansionInfo(ExpansionInfo info);
}
