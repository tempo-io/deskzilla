package com.almworks.bugzilla.integration;

import com.almworks.util.Enumerable;
import org.almworks.util.TypedKey;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugzillaAction extends Enumerable<BugzillaAction> {
  /**
   * Performs nothing.
   */
  public static final BugzillaAction NONE = new BugzillaAction(BugzillaHTMLConstants.KNOB_NONE);

  /**
   * Confirms bug and changes bug's state to ASSIGNED. 
   * Bugzilla 2.19: Bug cannot be reopened by ACCEPT.
   */
  public static final BugzillaAction ACCEPT = new BugzillaAction(BugzillaHTMLConstants.KNOB_ACCEPT);

  /**
   * Clears resolution. Unconditionally.
   */
  public static final BugzillaAction CLEAR_RESOLUTION = new BugzillaAction(BugzillaHTMLConstants.KNOB_CLEARRESOLUTION);

  /**
   * Changes status to CLOSED. Unconditionally.
   */
  public static final BugzillaAction CLOSE = new BugzillaAction(BugzillaHTMLConstants.KNOB_CLOSE);

  /**
   * Changes status to NEW, confirms bug. Does not reopen bug.
   */
  public static final BugzillaAction CONFIRM = new BugzillaAction(BugzillaHTMLConstants.KNOB_CONFIRM);

  /**
   * Changes status to RESOLVED, Resolution to DUPLICATE, and adds a comment that contains information about
   * duplication. Uses argument DUPLICATE_DUP_ID.
   */
  public static final BugzillaAction DUPLICATE = new BugzillaAction(BugzillaHTMLConstants.KNOB_DUPLICATE);
  public static final TypedKey<Integer> DUPLICATE_DUP_ID = TypedKey.create("dup_id");

  /**
   * Reassigns a bug to another user.
   * If the bug was closed, no status change is done.
   * If the bug was open and not confirmed, status remains UNCONFIRMED.
   * If the bug was open and confirmed, it changes state to NEW. (Even if it was ACCEPTED.)
   *
   * Argument: REASSIGN_AND_CONFIRM - do confirm before changing status.
   */
  public static final BugzillaAction REASSIGN = new BugzillaAction(BugzillaHTMLConstants.KNOB_REASSIGN);
  public static final TypedKey<String> REASSIGN_EMAIL = TypedKey.create("assigned_to");
  public static final TypedKey<Boolean> REASSIGN_AND_CONFIRM = TypedKey.create("andconfirm");

  /**
   * Assigns bug to initial owner, otherwise acting as REASSIGN action.
   * If QA_CONTACT field is in use, changes QA_CONTACT also to the initial qa contact for the component.
   * The form must have component defined (not 'dontchange').
   */
  public static final BugzillaAction REASSIGN_BY_COMPONENT =
    new BugzillaAction(BugzillaHTMLConstants.KNOB_REASSIGNBYCOMPONENT);
  public static final TypedKey<Boolean> REASSIGN_BY_COMPONENT_COMP_CONFIRM = TypedKey.create("compconfirm");

  /**
   * Changes state to either UNCONFIRMED or REOPEN (depending on was the bug ever confirmed).
   * Clears resolution.
   */
  public static final BugzillaAction REOPEN = new BugzillaAction(BugzillaHTMLConstants.KNOB_REOPEN);

  /**
   * Changes resolution to any except MOVED or DUPLICATE.
   * Changes bug status to RESOLVED.
   */
  public static final BugzillaAction RESOLVE = new BugzillaAction(BugzillaHTMLConstants.KNOB_RESOLVE);
  public static final TypedKey<String> RESOLVE_RESOLUTION = TypedKey.create("resolution");

  /**
   * Changes status to VERIFIED.
   */
  public static final BugzillaAction VERIFY = new BugzillaAction(BugzillaHTMLConstants.KNOB_VERIFY);


  private BugzillaAction(String name) {
    super(name);
  }
}
