/**********************************************************************
Copyright (c) 2009 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
**********************************************************************/
package org.datanucleus.store.appengine;

import org.datanucleus.store.appengine.Utils.Function;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for dealing with primitive types.
 *
 * @author Max Ross <maxr@google.com>
 */
final class PrimitiveUtils {

  private PrimitiveUtils() {}

  /**
   * Maps the result of <Primitive>.TYPE.getName() to value.
   * This is to get around the fact that you can't look up
   * primitive type classes using Class.forName()
   */
  public static final Map<String, Class<?>> PRIMITIVE_CLASSNAMES = buildPrimitiveClassnameMap();

  private static Map<String, Class<?>> buildPrimitiveClassnameMap() {
    Map<String, Class<?>> map = new HashMap<String, Class<?>>();
    map.put(Boolean.TYPE.getName(), Boolean.TYPE);
    map.put(Integer.TYPE.getName(), Integer.TYPE);
    map.put(Long.TYPE.getName(), Long.TYPE);
    map.put(Short.TYPE.getName(), Short.TYPE);
    map.put(Character.TYPE.getName(), Character.TYPE);
    map.put(Byte.TYPE.getName(), Byte.TYPE);
    map.put(Double.TYPE.getName(), Double.TYPE);
    map.put(Float.TYPE.getName(), Float.TYPE);
    return map;
  }

  /**
   * Gives us a fast way to get from a primitive type to a function that
   * transforms a primitive array of that type to a List of that type.
   */
  public static final
  Map<Class<?>, Function<Object, List<?>>> PRIMITIVE_ARRAY_TO_LIST_FUNC_MAP =
      buildPrimitiveArrayToListFuncMap();

  private static Map<Class<?>, Function<Object, List<?>>> buildPrimitiveArrayToListFuncMap() {
    Map<Class<?>, Function<Object, List<?>>> map =
        new HashMap<Class<?>, Function<Object, List<?>>>();
    map.put(
        Integer.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return Ints.asList((int[]) o);
          }
        });
    map.put(
        Long.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return Longs.asList((long[]) o);
          }
        });
    map.put(
        Short.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((short[]) o);
          }
        });
    map.put(
        Byte.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((byte[]) o);
          }
        });
    map.put(
        Float.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((float[]) o);
          }
        });
    map.put(
        Double.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((double[]) o);
          }
        });
    map.put(
        Boolean.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((boolean[]) o);
          }
        });
    map.put(
        Character.TYPE,
        new Function<Object, List<?>>() {
          public List<?> apply(Object o) {
            return PrimitiveArrays.asList((char[]) o);
          }
        });
    return map;
  }

  /**
   * Gives us a fast way to get from a primitive type to a function that
   * transforms a List of that type to a primitive array of that type.
   */
  public static final
  Map<Class<?>, Function<List<?>, Object>> LIST_TO_PRIMITIVE_ARRAY_FUNC_MAP =
      buildListToPrimitiveArrayFuncMap();

  private static Map<Class<?>, Function<List<?>, Object>> buildListToPrimitiveArrayFuncMap() {
    Map<Class<?>, Function<List<?>, Object>> map =
        new HashMap<Class<?>, Function<List<?>, Object>>();
    map.put(
        Integer.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return Ints.toArray((List<Integer>) list);
          }
        });
    map.put(
        Short.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toShortArray((List<Short>) list);
          }
        });
    map.put(
        Long.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return Longs.toArray((List<Long>) list);
          }
        });
    map.put(
        Character.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toCharArray((List<Character>) list);
          }
        });
    map.put(
        Float.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toFloatArray((List<Float>) list);
          }
        });
    map.put(
        Double.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toDoubleArray((List<Double>) list);
          }
        });
    map.put(
        Boolean.TYPE, new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toBooleanArray((List<Boolean>) list);
          }
        });
    map.put(
        Byte.TYPE,
        new Function<List<?>, Object>() {
          @SuppressWarnings("unchecked")
          public Object apply(List<?> list) {
            return PrimitiveArrays.toByteArray((List<Byte>) list);
          }
      });
    return map;
  }
}
