package com.almworks.database.bitmap;

import com.almworks.api.database.WCN;
import util.external.BitSet2;

public class BitmapIndexInfo {
  private final BitSet2 myBits;
  private final WCN myWCN;

  public BitmapIndexInfo(BitSet2 bits, WCN wcn) {
    myBits = bits;
    myWCN = wcn;
  }

  public BitSet2 getBits() {
    return myBits;
  }

  public WCN getWCN() {
    return myWCN;
  }
}
