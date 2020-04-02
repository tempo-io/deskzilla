package com.almworks.util.commons;

/**
 * @author dyoma
 */
public interface Function2<A, B, R> extends Function2E<A, B, R, Exception> {
  R invoke(A a, B b);
}
