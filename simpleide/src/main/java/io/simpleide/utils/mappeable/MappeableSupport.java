package io.simpleide.utils.mappeable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public interface MappeableSupport {
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
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
      type = type.getSuperclass();
    }
    return map;
  }

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
        } catch (IllegalAccessException | IllegalArgumentException e) {
          throw new RuntimeException("Erro ao alterar o atributo: " + field.getName(), e);
        }
      }

      type = type.getSuperclass();
    }
  }
}
