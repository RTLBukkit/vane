package org.oddlama.vane.annotation.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigDouble {
	double def();

	double min() default Double.NaN;

	double max() default Double.NaN;

	String desc();
}
