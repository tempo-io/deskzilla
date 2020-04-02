package com.almworks.bugzilla.integration.oper.js;

import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;

import java.text.ParseException;
import java.util.List;

public class JSTokenizerTests extends BaseTestCase {
  public static final String ID = ":id";
  public static final String NUM = ":num";
  public static final String CH = ":ch";
  public static final String STR = ":str";
  public static final String SUCCESS = ":success";
  public static final String FAILURE = ":failure";

  private MyVisitor myVisitor;

  protected void setUp() throws Exception {
    super.setUp();
    myVisitor = new MyVisitor();
  }

  protected void tearDown() throws Exception {
    myVisitor = null;
    super.tearDown();
  }

  public void testGeneral() throws ParseException {
    check(false, "var variable = new Array();", new String[] {
      ID, "var", ID, "variable", CH, "=", ID, "new", ID, "Array", CH, "(", CH, ")", CH, ";", SUCCESS});
  }

  public void testStrings() throws ParseException {
    check(false, "x = 'haba'", new String[] {ID, "x", CH, "=", STR, "haba", SUCCESS});
    check(false, "x = 'ha\\'ba'", new String[] {ID, "x", CH, "=", STR, "ha'ba", SUCCESS});
    check(false, "x = 'ha\\\\ba'", new String[] {ID, "x", CH, "=", STR, "ha\\ba", SUCCESS});
    check(false, "x = 'ha\\\"ba'", new String[] {ID, "x", CH, "=", STR, "ha\"ba", SUCCESS});
    check(false, "x = 'ha\\x40ba'", new String[] {ID, "x", CH, "=", STR, "ha@ba", SUCCESS});
    check(false, "x = 'ha\\x3ca'", new String[] {ID, "x", CH, "=", STR, "ha<a", SUCCESS});
    check(false, "x = \"haba\"", new String[] {ID, "x", CH, "=", STR, "haba", SUCCESS});
    check(false, "x = \"ha\\\"ba\"", new String[] {ID, "x", CH, "=", STR, "ha\"ba", SUCCESS});
    check(false, "x = \"ha\\\"\\\"ba\"", new String[] {ID, "x", CH, "=", STR, "ha\"\"ba", SUCCESS});

    check(true, "x ='sxsxsx", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\x", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\x3", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\x3'", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\x3z'", new String[] {ID, "x", CH, "=", FAILURE});
    check(true, "x ='sxsxsx\\xyz'", new String[] {ID, "x", CH, "=", FAILURE});
  }

  public void testIdentifiers() throws ParseException {
    check(false, "abc _abv 0axxx _ffff f9", new String[] {ID, "abc", ID, "_abv", NUM, "0", ID, "axxx", ID, "_ffff", ID, "f9", SUCCESS});
  }

  public void testNumbers() throws ParseException {
    check(false, "0 -1 12.43", new String[] {NUM, "0", CH, "-", NUM, "1", NUM, "12", CH, ".", NUM, "43", SUCCESS});
  }

  public void testSymbols() throws ParseException {
    check(false, "a[b[0][1]]{}", new String[] {
      ID, "a", CH, "[", ID, "b", CH, "[", NUM, "0", CH, "]", CH, "[", NUM, "1", CH, "]", CH, "]", CH, "{", CH, "}", SUCCESS});
  }

  public void testEOL() throws ParseException {
    check(false, "x\ny\rz\n\r\r\nabc\n", new String[] {ID, "x", ID, "y", ID, "z", ID, "abc", SUCCESS});
  }

  public void testComments() throws ParseException {
    check(false, "abc//this is abc\nxyz", new String[] {ID, "abc", ID, "xyz", SUCCESS});
    check(false, "abc /* this is abc\n   * a long comment here  \n\r\r\n * yea*/\nxyz",
      new String[] {ID, "abc", ID, "xyz", SUCCESS});
  }

  private void check(boolean mustThrow, String js, String[] tokens) throws ParseException {
    try {
      new JSTokenizer(js).visit(myVisitor);
      myVisitor.check(tokens);
      if (mustThrow)
        fail("didn't throw!");
    } catch (ParseException e) {
      myVisitor.check(tokens);
      if (mustThrow) {
        // normal
      } else {
        throw e;
      }
    }
  }


  public class MyVisitor implements JSTokenizerVisitor {
    private final List<String> myList = Collections15.arrayList();
    private final CollectionsCompare myCompare = new CollectionsCompare();

    public void visitFinish(boolean success) {
      myList.add(success ? SUCCESS : FAILURE);
    }

    public void visitIdentifier(String identifier) {
      myList.add(ID);
      myList.add(identifier);
    }

    public void visitNumberSequence(String numbers) {
      myList.add(NUM);
      myList.add(numbers);
    }

    public void visitSpecialChar(char c) {
      myList.add(CH);
      myList.add(Character.toString(c));
    }

    public void visitStart() {
    }

    public void visitStringLiteral(String literal) {
      myList.add(STR);
      myList.add(literal);
    }

    public void check(String[] expected) {
      myCompare.order(expected, myList);
      myList.clear();
    }
  }
}
