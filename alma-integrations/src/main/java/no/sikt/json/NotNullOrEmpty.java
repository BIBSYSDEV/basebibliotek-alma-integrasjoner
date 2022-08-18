package no.sikt.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks that a field is not null or empty (for strings), or not null for other types, for GSON deserialization.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotNullOrEmpty {
    String message() default "Field is null or empty";
}
