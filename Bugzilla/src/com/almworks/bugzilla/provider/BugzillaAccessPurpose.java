package com.almworks.bugzilla.provider;

import com.almworks.util.Enumerable;

public class BugzillaAccessPurpose extends Enumerable {
  public static final BugzillaAccessPurpose SYNCHRONIZATION = new BugzillaAccessPurpose("SYNCHRONIZATION");
  public static final BugzillaAccessPurpose IMMEDIATE_DOWNLOAD = new BugzillaAccessPurpose("IMMEDIATE_DOWNLOAD");
  public static final BugzillaAccessPurpose UPLOAD_QUEUE = new BugzillaAccessPurpose("UPLOAD_QUEUE");
  public static final BugzillaAccessPurpose ATTACHMENT_DOWNLOAD = new BugzillaAccessPurpose("ATTACHMENT_DOWNLOAD");

  private BugzillaAccessPurpose(String name) {
    super(name);
  }
}
