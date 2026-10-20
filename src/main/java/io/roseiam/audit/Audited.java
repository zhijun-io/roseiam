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

    int changedFieldsParameter() default -1;

    String[] changedFields() default {};

    int actorParameter() default -1;
}
