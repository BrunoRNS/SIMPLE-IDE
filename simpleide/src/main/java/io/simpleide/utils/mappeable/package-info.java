/**
 * Reflective conversion between annotated objects and string-keyed maps.
 *
 * <p>
 * {@link io.simpleide.utils.mappeable.Mappeable} marks a type as eligible for map
 * conversion at runtime. {@link io.simpleide.utils.mappeable.MappeableSupport} provides
 * default {@code getMap()} and {@code setMap(Map)} methods that read and write the
 * non-static fields of the implementing class and its superclasses, keyed by field name.
 *
 * <p>
 * The map is a snapshot. Mutating the map does not update the object until {@code setMap}
 * is called, and {@code setMap} replaces the fields it finds in the map. Values are
 * exposed without copying, so mutable values inside the map should be treated as
 * read-only. Conversion is intended for simple configuration and diagnostic data, not for
 * arbitrary object graphs or persistence.
 *
 * <p>
 * Conversion is reflection-based and therefore sensitive to field renames and module
 * access restrictions. Prefer explicit accessors in public APIs, and keep mapped types in
 * exported packages.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/api/util/utilities.md">docs/api/util/utilities.md</a>
 */
package io.simpleide.utils.mappeable;
