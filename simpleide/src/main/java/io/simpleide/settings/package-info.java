/**
 * Persistent application settings and customizable keyboard shortcuts.
 *
 * <p>
 * {@link io.simpleide.settings.SettingsService} stores the JSON document under
 * {@code ~/.simpleide/settings.json}, {@link io.simpleide.settings.IdeSettings} models
 * the values and {@link io.simpleide.settings.KeybindingManager} installs the configured
 * shortcuts through the thread manager.
 *
 */
package io.simpleide.settings;
