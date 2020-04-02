package com.almworks.bugzilla.integration;

import com.almworks.util.tests.BaseTestCase;

public class BugzillaIntegrationTests extends BaseTestCase {
  public void test() {
    assertTrue(BugzillaIntegration.isVersionOrLater("3.2", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.3", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.4", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.4.rc1", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.2.1", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("4", "3.2"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.2", "3"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3", "3"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.1.2", "3.1.1.1"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.1.1.3", "3.1.1.1"));
    assertTrue(BugzillaIntegration.isVersionOrLater("3.1.1.1.3", "3.1.1.1"));

    assertFalse(BugzillaIntegration.isVersionOrLater("3", "3.2"));
    assertFalse(BugzillaIntegration.isVersionOrLater("3.1", "3.2"));
    assertFalse(BugzillaIntegration.isVersionOrLater("3.1.9", "3.2"));
    assertFalse(BugzillaIntegration.isVersionOrLater("rc1", "3.2"));
    assertFalse(BugzillaIntegration.isVersionOrLater("2", "3.2"));
    assertFalse(BugzillaIntegration.isVersionOrLater("2", "3"));
    assertFalse(BugzillaIntegration.isVersionOrLater("3.1.1", "3.1.1.1"));
    assertFalse(BugzillaIntegration.isVersionOrLater("3", "3.1.1.1"));
    assertFalse(BugzillaIntegration.isVersionOrLater("3.1.alpha.1", "3.1.1.1"));
  }
}
