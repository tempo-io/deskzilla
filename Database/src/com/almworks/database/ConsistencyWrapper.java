package com.almworks.database;

import com.almworks.api.inquiry.AbstractInquiryData;
import com.almworks.api.inquiry.InquiryKey;

/**
 * :todoc:
 *
 * @author sereda
 */
public interface ConsistencyWrapper {
  InquiryKey<InquiryData> DATABASE_INCONSISTENT = InquiryKey.inquiryKey("databaseInconsistent", InquiryData.class);

  ConsistencyWrapper FAKE = new ConsistencyWrapper() {
    public <T> T run(Inconsistent<T> inconsistent) {
      try {
        return inconsistent.run();
      } catch (DatabaseInconsistentException e) {
        handle(e, 0);
        return null;
      }
    }

    public void handle(DatabaseInconsistentException exception, int attempt) {
      throw new RuntimeDatabaseInconsistentException(exception);
    }
  };

  void handle(DatabaseInconsistentException exception, int attempt);

  @Deprecated
  <T> T run(Inconsistent<T> inconsistent);

  public static class InquiryData extends AbstractInquiryData {
    private final DatabaseInconsistentException myException;

    private NormalConsistencyWrapper.Option myChoice;

    public InquiryData(DatabaseInconsistentException exception) {
      myException = exception;
    }

    public NormalConsistencyWrapper.Option getChoice() {
      return myChoice;
    }

    public void setChoice(NormalConsistencyWrapper.Option choice) {
      myChoice = choice;
    }

    public DatabaseInconsistentException getException() {
      return myException;
    }
  }


  public static class Option {
    public static final NormalConsistencyWrapper.Option ABORT = new NormalConsistencyWrapper.Option();
    public static final NormalConsistencyWrapper.Option ERROR = new NormalConsistencyWrapper.Option();
    public static final NormalConsistencyWrapper.Option RETRY = new NormalConsistencyWrapper.Option();

    private Option() {
    }
  }
}
