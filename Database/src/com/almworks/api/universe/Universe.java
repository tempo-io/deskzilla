package com.almworks.api.universe;

import java.util.Map;


/**
 * :todoc:
 *
 * @author sereda
 */
public interface Universe {
  long BIG_BANG = 0;
  long END_OF_THE_UNIVERSE = Long.MAX_VALUE - Integer.MAX_VALUE;

  /**
   * Universe Change Number is the monotonously increasing change number of all universe.
   * All atoms added within the same {@link Expansion} will have the same UCN, equal to the
   * UCN of the Universe that was <b>before</b> commit started.
   * <p/>
   * Thus, {@link #getUCN} returns the UCN of the next transaction.
   *
   * @return Universe Change Number
   */
  long getUCN();

  Atom getAtom(long atomID);

  Index createIndex(IndexInfo indexInfo);

  Index[] getIndices();

  Index[] getIndices(Atom atom);

  Index getIndex(String name);

  Index getIndex(int indexID);

  Index getGlobalIndex();

  boolean isDefaultIndexing();

  void setDefaultIndexing(boolean defaultIndexing);

  Expansion begin();

  Map<String, String> getCustomProperties();

  void setCustomPropertiesIfCreating(Map<String, String> metadata);
}
