package com.almworks.util.threads;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({METHOD, TYPE, CONSTRUCTOR})
public @interface CanBlock {
  String value() default "should not be called from AWT thread";
  String becauseOf() default "";
}
