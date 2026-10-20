package io.roseiam.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();

    String targetType();

    int targetIdParameter() default -1;

    boolean targetIdFromResult() default false;

    int actionParameter() default -1;

    String actionWhenTrue() default "";

    String actionWhenFalse() default "";

    boolean recordFailure() default false;

    boolean recordOnlyWhenResultTrue() default false;

    boolean recordOnlyWhenResultNotNull() default false;
}
