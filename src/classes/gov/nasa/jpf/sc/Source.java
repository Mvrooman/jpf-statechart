package gov.nasa.jpf.sc;

import java.lang.annotation.*;


/**
 * a generic link annotation, value being the file source
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Source {
	String value();
}
