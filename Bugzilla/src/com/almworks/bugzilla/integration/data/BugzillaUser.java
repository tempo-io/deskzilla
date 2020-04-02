package com.almworks.bugzilla.integration.data;

import org.almworks.util.Util;
import org.jetbrains.annotations.*;

public class BugzillaUser {
  private final String myEmail;
  private final String myDisplayName;
  private final boolean myDisplayNameKnown;

  private BugzillaUser(String email, @Nullable String displayName, boolean displayNameKnown) {
    if (email == null || email.length() == 0) throw new NullPointerException(String.valueOf(email));
    email = email.trim();
    if (displayName != null) {
      displayName = displayName.trim();
      if (displayName.length() == 0) displayName = null;
    }
    if (displayName == null) {
      displayName = email;
      displayNameKnown = false;
    }
    myEmail = Util.lower(email);
    myDisplayName = displayName;
    myDisplayNameKnown = displayNameKnown;
  }

  public String getEmailId() {
    return myEmail;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  public boolean isDisplayNameKnown() {
    return myDisplayNameKnown;
  }

  @NotNull
  public BugzillaUser mergeWith(@Nullable BugzillaUser other) {
    if (other == null) return this;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null) return false;
    if (!(obj instanceof BugzillaUser)) return false;
    BugzillaUser other = (BugzillaUser) obj;
    return myEmail.equals(other.myEmail);
  }

  @Override
  public int hashCode() {
    return myEmail.hashCode();
  }

  /**
   * Creates not null user if shortEmail is not null and not empty.
   * @param shortEmail short (without email suffix) email
   * @param displayName displayable name if surely known. Should be null or empty if display name is not known.
   * @param emailSuffix email suffix. Pass empty or null if not using email suffix
   * @return user identified by email appended suffix
   */
  @Nullable
  public static BugzillaUser shortEmailName(@Nullable String shortEmail, @Nullable String displayName, @Nullable String emailSuffix) {
    shortEmail = normalizeString(shortEmail);
    if (shortEmail == null) return null;
    displayName = normalizeString(displayName);
    String name = displayName != null ? displayName : shortEmail;
    if (emailSuffix == null) emailSuffix = "";
    emailSuffix = emailSuffix.trim();
    return new BugzillaUser(shortEmail + emailSuffix, name, displayName != null);
  }

  /**
   * Creates not null user if email is not null and not empty
   * @param email email (with suffix)
   * @param displayName displayable name if surely known (otherwise should be empty or null)
   * @return user identified by email
   */
  @Nullable
  public static BugzillaUser longEmailName(@Nullable String email, @Nullable String displayName) {
    email = normalizeString(email);
    if (email == null) return null;
    return new BugzillaUser(email, normalizeString(displayName), true);
  }

  @Nullable
  private static String normalizeString(@Nullable String str) {
    if (str != null) {
      str = str.trim();
      if (str.length() == 0) str = null;
    }
    return str;
  }

  public boolean equalId(String email) {
    return email != null && myEmail.equalsIgnoreCase(email);
  }

  @Override
  public String toString() {
    String suffix = myEmail.indexOf(myDisplayName) >= 0 ? "" : " (" + myDisplayName + ")";
    return myEmail + suffix;
  }
}
