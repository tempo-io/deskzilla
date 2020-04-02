package com.almworks.bugzilla.integration;

import com.almworks.api.connector.ConnectorStateStorage;
import com.almworks.api.http.HttpMaterial;
import com.almworks.bugzilla.integration.oper.AuthenticationMaster;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.TimeZone;

public class ServerInfo {
  private final HttpMaterial myMaterial;
  private final String myBaseURL;
  private final ConnectorStateStorage myStateStorage;
  private final TimeZone myDefaultTimezone;
  private final AuthenticationMaster myMaster;
  private volatile BugzillaVersion myBzVersion;
  @Nullable
  private final String myEmailSuffix;

  public ServerInfo(HttpMaterial material, String baseURL, ConnectorStateStorage stateStorage, TimeZone defaultTimezone,
    AuthenticationMaster master, @Nullable String emailSuffix, @Nullable String bzVersion)
  {
    myMaterial = material;
    myBaseURL = baseURL;
    myStateStorage = stateStorage;
    myDefaultTimezone = defaultTimezone;
    myMaster = master;
    myEmailSuffix = emailSuffix;
    myBzVersion = BugzillaVersion.parse(bzVersion);
  }

  @Nullable
  public String getEmailSuffix() {
    return myEmailSuffix;
  }

  public HttpMaterial getMaterial() {
    return myMaterial;
  }

  public String getBaseURL() {
    return myBaseURL;
  }

  public ConnectorStateStorage getStateStorage() {
    return myStateStorage;
  }

  public TimeZone getDefaultTimezone() {
    return myDefaultTimezone;
  }

  public AuthenticationMaster getMaster() {
    return myMaster;
  }

  public long getPersistentLong(String key) {
    return myStateStorage.getPersistentLong(key);
  }

  public String removeSuffix(BugzillaAttribute attribute, String value) {
    if (BugzillaAttribute.ASSIGNED_TO.equals(attribute) ||
      BugzillaAttribute.QA_CONTACT.equals(attribute)) value = removeSuffix(value);
    return value;
  }

  public String removeSuffix(String fullUserName) {
    if (fullUserName == null || fullUserName.length() == 0 || myEmailSuffix == null || myEmailSuffix.length() == 0) return fullUserName;
    if (!fullUserName.endsWith(myEmailSuffix)) Log.warn("Missing user name suffix " + fullUserName + " " + myEmailSuffix);
    else fullUserName = fullUserName.substring(0, fullUserName.length() - myEmailSuffix.length());
    return fullUserName;
  }

  public void updateVersion(String version) {
    BugzillaVersion newValue = BugzillaVersion.parse(version);
    if (newValue != null) myBzVersion = newValue;
  }
  
  public boolean versionAtLeast(@NotNull BugzillaVersion leastExpectedVersion) {
     return leastExpectedVersion.compareTo(myBzVersion) <= 0;
  }

  @Nullable
  public BugzillaVersion getBzVersion() {
    return myBzVersion;
  }
}
