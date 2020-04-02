package com.almworks.bugzilla.provider.datalink.schema.custom;

import com.almworks.bugzilla.integration.data.CustomFieldType;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.util.List;

public class FieldTypeTests extends BaseTestCase {
  private final FieldType CHOICE = FieldType.fromExternal(CustomFieldType.CHOICE, null);
  private final FieldType MULTI = FieldType.fromExternal(CustomFieldType.MULTI_SELECT, null);

  final List<String> nullList = null;
  final List<String> emptyList = Collections15.emptyList();
  final List<String> singleNull = Collections15.arrayList((String)null);
  final List<String> singleEmpty = Collections15.arrayList("");
  final List<String> singleSpace = Collections15.arrayList(" ");

  final List<String> singleString = Collections15.arrayList("foo");
  final List<String> twoStrings = Collections15.arrayList("foo", "bar");
  final List<String> singleNumber = Collections15.arrayList("123");
  final List<String> twoNumbers = Collections15.arrayList("123", "456");
  final List<String> singleDate = Collections15.arrayList("2011-04-12 16:37");
  final List<String> singleEpoch = Collections15.arrayList("1970-01-01 00:00 GMT");
  final List<String> singleBeforeEpoch = Collections15.arrayList("1960-01-01 00:00 GMT");
  final List<String> twoDates = Collections15.arrayList("2011-04-12 16:37", "2011-04-13 16:38");

  private void check(FieldType type, String name, List<String> list, boolean accepts) {
    assertTrue(
      name + (accepts ? " didn't accept " : " accepted ") + list,
      type.acceptsRawValue(list) == accepts);
  }

  private void checkUnknown(List<String> list, boolean accepts) {
    check(FieldType.UNKNOWN, "UNKNOWN", list, accepts);
  }

  private void checkBugId(List<String> list, boolean accepts) {
    check(FieldType.BUG_ID, "BUG_ID", list, accepts);
  }

  private void checkDate(List<String> list, boolean accepts) {
    check(FieldType.DATE_TIME, "DATE_TIME", list, accepts);
  }

  private void checkText(List<String> list, boolean accepts) {
    check(FieldType.TEXT, "TEXT", list, accepts);
  }

  private void checkLongText(List<String> list, boolean accepts) {
    check(FieldType.LONG_TEXT, "LONG_TEXT", list, accepts);
  }

  private void checkChoice(List<String> list, boolean accepts) {
    check(CHOICE, "CHOICE", list, accepts);
  }

  private void checkMulti(List<String> list, boolean accepts) {
    check(MULTI, "MULTI", list, accepts);
  }

  public void testAlwaysAccept() throws Exception {
    final List<List<String>> alwaysAccept =
      Collections15.arrayList(nullList, emptyList, singleNull, singleEmpty, singleSpace);

    for(final List<String> list : alwaysAccept) {
      checkUnknown(list, true);
      checkBugId(list, true);
      checkDate(list, true);
      checkText(list, true);
      checkLongText(list, true);
      checkChoice(list, true);
      checkMulti(list, true);
    }
  }

  public void testUnknown() throws Exception {
    checkUnknown(singleString, true);
    checkUnknown(twoStrings, true);
    checkUnknown(singleNumber, true);
    checkUnknown(twoNumbers, true);
    checkUnknown(singleDate, true);
    checkUnknown(twoDates, true);
  }

  public void testBugID() throws Exception {
    checkBugId(singleString, false);
    checkBugId(twoStrings, false);
    checkBugId(singleNumber, true);
    checkBugId(twoNumbers, false);
    checkBugId(singleDate, false);
    checkBugId(twoDates, false);
  }

  public void testDate() throws Exception {
    checkDate(singleString, false);
    checkDate(twoStrings, false);
    checkDate(singleNumber, false);
    checkDate(twoNumbers, false);
    checkDate(singleDate, true);
    checkDate(singleEpoch, true);
    checkDate(singleBeforeEpoch, true);
    checkDate(twoDates, false);
  }

  public void testText() throws Exception {
    checkText(singleString, true);
    checkText(twoStrings, false);
    checkText(singleNumber, true);
    checkText(twoNumbers, false);
    checkText(singleDate, true);
    checkText(twoDates, false);
  }

  public void testLongText() throws Exception {
    checkLongText(singleString, true);
    checkLongText(twoStrings, false);
    checkLongText(singleNumber, true);
    checkLongText(twoNumbers, false);
    checkLongText(singleDate, true);
    checkLongText(twoDates, false);
  }

  public void testChoice() throws Exception {
    checkChoice(singleString, true);
    checkChoice(twoStrings, false);
    checkChoice(singleNumber, true);
    checkChoice(twoNumbers, false);
    checkChoice(singleDate, true);
    checkChoice(twoDates, false);
  }

  public void testMulti() throws Exception {
    checkMulti(singleString, true);
    checkMulti(twoStrings, true);
    checkMulti(singleNumber, true);
    checkMulti(twoNumbers, true);
    checkMulti(singleDate, true);
    checkMulti(twoDates, true);
  }
}
