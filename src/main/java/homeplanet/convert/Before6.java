package homeplanet.convert;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks code outside this package that is there only for a fleet from before 6.0 (a crew register's crew.txt, say),
 * where it is too much a part of its class to move here. When this package is deleted, the compiler points at each one.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Before6 {
	/** What it reads, and from which versions. */
	String value() default "";
}
