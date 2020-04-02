package com.almworks.database;

import com.almworks.api.exec.ApplicationManager;
import com.almworks.api.inquiry.*;
import com.almworks.util.CantGetHereException;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;

/**
 * :todoc:
 *
 * @author sereda
 */
class NormalConsistencyWrapper implements ConsistencyWrapper {
  private final Inquiries myInquiries;
  private final ApplicationManager myApplicationManager;

  public NormalConsistencyWrapper(Inquiries inquiries, ApplicationManager applicationManager) {
    assert inquiries != null;
    assert applicationManager != null;
    myInquiries = inquiries;
    myApplicationManager = applicationManager;
  }

  public <T> T run(Inconsistent<T> inconsistent) {
    while (true) {
      try {
        return inconsistent.run();
      } catch (DatabaseInconsistentException e) {
        handle(e, -1);
      }
    }
  }

  public void handle(DatabaseInconsistentException exception, int attempt) {
    try {
      Log.debug("db inconsistent (#" + attempt + ")", exception);
      InquiryData data = myInquiries.inquire(InquiryLevel.CRITICAL, DATABASE_INCONSISTENT, new InquiryData(exception));
      if (data == null || !data.isAnswered() || data.getChoice() == Option.ERROR)
        throw new RuntimeDatabaseInconsistentException(exception);
      if (data.getChoice() == Option.RETRY)
        return;
      if (data.getChoice() == Option.ABORT) {
        myApplicationManager.requestExit();
        // should never access here, but...
        throw new RuntimeDatabaseInconsistentException(exception);
      }

      // should never access here too
      throw new CantGetHereException();
    } catch (UnhandleableInquiry ee) {
      Log.warn("unhandleable inquiry: " + ee.getInquiryKey());
      throw new RuntimeDatabaseInconsistentException(exception);
    } catch (InterruptedException ee) {
      throw new RuntimeInterruptedException(ee);
    } catch (Exception ee) {
      Log.error("caught an exception while handling database inconsistency", ee);
      throw new RuntimeDatabaseInconsistentException(ee);
    }
  }
}
