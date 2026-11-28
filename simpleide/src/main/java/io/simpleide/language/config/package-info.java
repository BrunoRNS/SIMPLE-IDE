/**
 * Language registration and platform detection.
 *
 * <p>
 * {@link io.simpleide.language.config.LangConfig} maps file extensions to language
 * references, while {@link io.simpleide.language.config.LangSetup} installs every
 * toolchain that is available on the current system.
 * {@link io.simpleide.language.config.LangPlatform} describes the supported operating
 * system and architecture combinations, and
 * {@link io.simpleide.language.config.LangConfigHolder} exposes the shared configuration
 * instance.
 *
 * <p>
 * See docs/reference/LANGUAGES.md for the registration and discovery rules.
 *
 * @see <a href= "https://github.com/BrunoRNS/SIMPLE-
 * IDE/blob/main/docs/reference/LANGUAGES.md">docs/reference/LANGUAGES.md</a>
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 */
package io.simpleide.language.config;
