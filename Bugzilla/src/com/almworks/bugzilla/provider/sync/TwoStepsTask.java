package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.util.progress.*;

/** This task is the first step.<br>
 *
 *  Progress inherited from {@link LinearProgressTask} is the first step's progress.<br>
 *  Progress returned from {@link #getProgress()} is the overall progress (the progress that counts both steps).<br> */
public abstract class TwoStepsTask extends LinearProgressTask {
  private Progress myAggregate;
  private Task mySecondTask;

  protected TwoStepsTask(SyncController controller, String name, String displayableName, int duration, boolean firstStepImmediate) {
    super(controller, name, displayableName, duration, firstStepImmediate);
  }

  public ProgressSource getProgress() {
    assert myAggregate != null;
    return myAggregate;
  }

  protected void transfer() throws CancelledException {
    assert mySecondTask != null;
    myProgress.setDone();
    myController.runTask(mySecondTask);
  }

  protected void setSecondTask(Task task) {
    assert mySecondTask == null;
    mySecondTask = task;
//    MyConvertor producer = new MyConvertor();
    myAggregate = new Progress(myName + "+" + task.getName());
    Progress p1 = myProgress;
    int d1 = getDuration();
    ProgressSource p2 = mySecondTask.getProgress();
    int d2 = mySecondTask.getDuration();
    new ProgressWeightHelper().addSource(p1, d1).addSource(p2, d2).insertTo(myAggregate);
  }


  @Override
  protected void setDone() {
    myAggregate.setDone();
  }

  //  private class MyConvertor extends ProgressAggregator.StringActivityProducer {
//    public String convert(List<ProgressSource<Object>> progressSources) {
//      String name = getDisplayableName();
//      String superResult = super.convert(progressSources);
//      if (superResult != null && !name.equals(superResult)) {
//        return name + " (" + superResult + ")";
//      } else {
//        return name;
//      }
//    }
//  }
}
