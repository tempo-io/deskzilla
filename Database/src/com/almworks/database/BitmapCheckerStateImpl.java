package com.almworks.database;

import com.almworks.api.database.IndexCheckerState;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.threads.CanBlock;
import org.almworks.util.Const;

public class BitmapCheckerStateImpl implements IndexCheckerState {
  private final BasicScalarModel<String> myState = BasicScalarModel.createWithValue(null, true);

  @CanBlock
  public void setState(String state) {
    myState.setValue(state);
    // let ui listeners show the value
    ThreadGate.AWT_IMMEDIATE.execute(Const.EMPTY_RUNNABLE);
  }

  public ScalarModel<String> getState() {
    return myState;
  }
}
