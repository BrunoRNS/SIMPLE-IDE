package io.simpleide.utils.mappeable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as eligible for reflective map conversion.
 *
 * <p>
 * The marker is read at runtime by tooling that works with {@link MappeableSupport}. It
 * has no effect on its own.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mappeable {

}
