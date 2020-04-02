package com.almworks.util.threads;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({METHOD})
public @interface AvoidLocks {
  String value() default "avoid calling this method under locks - deadlocks are possible";
}
