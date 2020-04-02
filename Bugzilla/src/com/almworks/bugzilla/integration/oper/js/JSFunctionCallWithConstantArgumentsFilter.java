package com.almworks.bugzilla.integration.oper.js;

import org.almworks.util.Collections15;
import org.almworks.util.Log;

import java.math.BigDecimal;
import java.util.List;

public abstract class JSFunctionCallWithConstantArgumentsFilter extends JSParserDelegator {
  protected JSFunctionCallWithConstantArgumentsFilter() {
    setDelegate(new Initial());
  }

  protected abstract boolean acceptFunctionName(String name);
  
  protected abstract void visitFunctionCall(String name, List<Object> arguments);

  private class Initial extends JSParserAdapter {
    private int myState;
    private final List<Object> myArguments = Collections15.arrayList();
    private String myName;
    private List myCurrentList;

    @Override
    public void onDelegated() {
      myState = 0;
    }

    @Override
    public void visitStatementStart() {
      myState = 1;
      myName = null;
      myArguments.clear();
    }

    @Override
    public void visitIdentifier(String identifier) {
      if (myState == 1 && acceptFunctionName(identifier)) {
        myName = identifier;
        myState = 2;
      } else {
        myState = -1;
      }
    }

    @Override
    protected <T> void visitToken(TokenType<T> type, T value) {
      myState = -1;
    }

    @Override
    public void visitSpecialChar(char c) {
      if (myState == 2 && c == '(') {
        myState = 3;
        assert myArguments.isEmpty() : myArguments;
      } else if (myState == 4 && c == ',') {
        myState = 3;
      } else if (myState == 3002 && c == ',') {
        myState = 3001;
      } else if (myState == 3 && c == '[') {
        myState = 3001;
        myCurrentList = Collections15.arrayList();
      } else if (myState == 3002 && c == ']') {
        myState = 4;
        myArguments.add(myCurrentList);
        myCurrentList = null;
      } else if (myState == 4 && c == ')') {
        visitFunctionCall(myName, myArguments);
        myState = -1;
      } else {
        super.visitSpecialChar(c);
      }
    }

    @Override
    public void visitStringLiteral(String literal) {
      if (myState == 3) {
        myArguments.add(literal);
        myState = 4;
      } else if (myState == 3001) {
        myCurrentList.add(literal);
        myState = 3002;
      } else {
        super.visitStringLiteral(literal);
      }
    }

    @Override
    public void visitNumberSequence(String numbers) {
      if (myState == 3) {
        Object v = getNumber(numbers);
        if (v != null) {
          myArguments.add(v);
          myState = 4;
        } else {
          myState = -1;
        }
      } else if (myState == 3001) {
        Object v = getNumber(numbers);
        if (v != null) {
          myCurrentList.add(v);
          myState = 3002;
        } else {
          myState = -1;
        }
      } else {
        super.visitNumberSequence(numbers);
      }
    }

    private Object getNumber(String numbers) {
      try {
        return Integer.parseInt(numbers);
      } catch (NumberFormatException e) {
        try {
          return new BigDecimal(numbers);
        } catch (NumberFormatException e1) {
          Log.warn(e1);
          return null;
        }
      }
    }
  }
}
