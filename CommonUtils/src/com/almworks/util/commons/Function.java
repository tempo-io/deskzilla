package com.almworks.util.commons;

public interface Function<A, R> extends FunctionE<A, R, Exception> {
  R invoke(A argument);

  class Const<A, R> implements Function<A, R> {
    private final R myResult;

    public Const(R result) {
      myResult = result;
    }

    public R invoke(A argument) {
      return myResult;
    }

    public static <A, R> Function<A, R> create(R result) {
      return new Const<A,R>(result);
    }
  }
}
