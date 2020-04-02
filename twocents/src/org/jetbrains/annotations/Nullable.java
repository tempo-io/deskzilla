package org.jetbrains.annotations;

import java.lang.annotation.*;

@Documented
@Retention(value = java.lang.annotation.RetentionPolicy.SOURCE)
@Target(
  value = {java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.LOCAL_VARIABLE})
public @interface Nullable {
  String documentation() default "";
}