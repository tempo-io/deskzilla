package com.almworks.bugzilla.integration;

import com.almworks.bugzilla.integration.data.BugzillaUser;

public interface BugzillaAccountNameSink {
  void updateAccountName(BugzillaUser accountName);
}
