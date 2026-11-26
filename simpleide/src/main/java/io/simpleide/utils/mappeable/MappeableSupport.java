package io.simpleide.utils.mappeable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default reflective conversion of an object's fields to and from a map.
 *
 * <p>
 * Non-static, non-synthetic fields declared by the implementation and its superclasses
 * are included. Field names are used as keys, and the nearest declaration wins when a
 * subclass hides a superclass field. Access is forced, so implementations should not rely
 * on module boundaries for encapsulation.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public interface MappeableSupport {

	/**
	 * Builds a map with the current value of every instance field.
	 * @return an insertion-ordered map of field names to values
	 * @throws RuntimeException when a field cannot be accessed
	 */
	default Map<String, Object> getMaping() {

		Map<String, Object> map = new LinkedHashMap<>();
		Class<?> type = getClass();

		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
					continue;
				}

				try {
					field.setAccessible(true);
					map.putIfAbsent(field.getName(), field.get(this));
				}
				catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
			type = type.getSuperclass();
		}
		return map;
	}

	/**
	 * Writes values from a map into matching instance fields.
	 *
	 * <p>
	 * Fields without a key in the map are left unchanged. A value with an incompatible
	 * type fails the whole operation at the offending field.
	 * @param map field names and values to assign
	 * @throws RuntimeException when a field cannot be accessed or the value type is
	 * incompatible
	 */
	default void setMaping(Map<String, Object> map) {
		Class<?> type = getClass();

		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
					continue;
				}

				if (!map.containsKey(field.getName())) {
					continue;
				}

				try {
					field.setAccessible(true);
					field.set(this, map.get(field.getName()));
				}
				catch (IllegalAccessException | IllegalArgumentException e) {
					throw new RuntimeException("Erro ao alterar o atributo: " + field.getName(), e);
				}
			}

			type = type.getSuperclass();
		}
	}

}
