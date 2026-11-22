package io.simpleide.settings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.simpleide.thread.ThreadMaster;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and stores the application settings under {@code ~/.simpleide/settings.json}.
 *
 * <p>
 * The service keeps one in-memory settings instance and writes an indented JSON document
 * whenever a value changes. Files are replaced atomically when the file system supports
 * it. Listeners are notified on the Swing Event Dispatch Thread through
 * {@link ThreadMaster}, so views can update controls without doing their own thread
 * management. Missing or corrupt files fall back to the defaults.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class SettingsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SettingsService.class);

	private static final String SETTINGS_DIRECTORY = ".simpleide";

	private static final String SETTINGS_FILE = "settings.json";

	private static final SettingsService INSTANCE = new SettingsService(defaultSettingsPath());

	private final Path settingsPath;

	private final ObjectMapper mapper;

	private final List<Consumer<IdeSettings>> listeners = new CopyOnWriteArrayList<>();

	private IdeSettings settings;

	/**
	 * Returns the process-wide settings service.
	 * @return the singleton service
	 */
	public static SettingsService getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the default settings file location.
	 * @return {@code ~/.simpleide/settings.json}
	 */
	public static Path defaultSettingsPath() {
		return Path.of(System.getProperty("user.home", "."), SETTINGS_DIRECTORY, SETTINGS_FILE);
	}

	/**
	 * Creates a service backed by the default settings file.
	 */
	public SettingsService() {
		this(defaultSettingsPath());
	}

	/**
	 * Creates a service backed by an explicit file.
	 * @param settingsPath settings file location
	 */
	public SettingsService(Path settingsPath) {
		this.settingsPath = Objects.requireNonNull(settingsPath, "settingsPath").toAbsolutePath().normalize();
		this.mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(SerializationFeature.INDENT_OUTPUT);
	}

	/**
	 * Returns the settings path used by this service.
	 * @return the normalized settings file location
	 */
	public Path getSettingsPath() {
		return settingsPath;
	}

	/**
	 * Returns the current settings, loading the file on first use.
	 * @return the current settings
	 */
	public synchronized IdeSettings getSettings() {
		if (settings == null) {
			settings = readSettings();
		}
		return settings;
	}

	/**
	 * Replaces the current settings and writes them to disk.
	 * @param newSettings settings to store
	 */
	public void setSettings(IdeSettings newSettings) {
		IdeSettings normalized = new IdeSettings(Objects.requireNonNull(newSettings, "newSettings"));
		synchronized (this) {
			settings = normalized;
			writeSettings(normalized);
		}
		notifyListeners(normalized);
	}

	/**
	 * Applies a change to the current settings, stores the result and notifies listeners.
	 * @param change change applied to the current settings
	 * @return the stored settings
	 */
	public IdeSettings update(Consumer<IdeSettings> change) {
		Objects.requireNonNull(change, "change");
		IdeSettings updated;
		synchronized (this) {
			IdeSettings current = getSettings();
			change.accept(current);
			current.normalize();
			updated = new IdeSettings(current);
			settings = updated;
			writeSettings(updated);
		}
		notifyListeners(updated);
		return updated;
	}

	/**
	 * Restores every setting to its default value, stores it and notifies listeners.
	 * @return the stored settings
	 */
	public IdeSettings resetToDefaults() {
		IdeSettings defaults = new IdeSettings();
		defaults.resetToDefaults();
		defaults.normalize();
		setSettings(defaults);
		return defaults;
	}

	/**
	 * Registers a settings listener.
	 * @param listener listener invoked on the EDT, must not be {@code null}
	 */
	public void addListener(Consumer<IdeSettings> listener) {
		listeners.add(Objects.requireNonNull(listener, "listener"));
	}

	/**
	 * Removes a settings listener.
	 * @param listener listener to remove
	 */
	public void removeListener(Consumer<IdeSettings> listener) {
		listeners.remove(listener);
	}

	private IdeSettings readSettings() {
		if (!Files.isRegularFile(settingsPath)) {
			return new IdeSettings();
		}
		try {
			IdeSettings loaded = mapper.readValue(settingsPath.toFile(), IdeSettings.class);
			if (loaded == null) {
				return new IdeSettings();
			}
			loaded.normalize();
			return loaded;
		}
		catch (IOException | RuntimeException error) {
			LOGGER.warn("Unable to read settings from {}; using defaults.", settingsPath, error);
			return new IdeSettings();
		}
	}

	private void writeSettings(IdeSettings settings) {
		Path parent = settingsPath.getParent();
		if (parent == null) {
			throw new IllegalStateException("Settings path has no parent directory: " + settingsPath);
		}
		try {
			Files.createDirectories(parent);
			Path temporary = Files.createTempFile(parent, "settings-", ".json");
			try {
				mapper.writeValue(temporary.toFile(), settings);
				moveIntoPlace(temporary, settingsPath);
			}
			finally {
				Files.deleteIfExists(temporary);
			}
		}
		catch (IOException error) {
			LOGGER.error("Unable to save settings to {}.", settingsPath, error);
		}
	}

	private void moveIntoPlace(Path temporary, Path target) throws IOException {
		try {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException error) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void notifyListeners(IdeSettings updated) {
		if (listeners.isEmpty()) {
			return;
		}
		try {
			ThreadMaster.getInstance().executeOnEDT(() -> {
				for (Consumer<IdeSettings> listener : listeners) {
					try {
						listener.accept(updated);
					}
					catch (RuntimeException error) {
						LOGGER.warn("A settings listener failed.", error);
					}
				}
			});
		}
		catch (IllegalStateException error) {
			LOGGER.debug("Settings listeners were not notified because the thread manager is shut down.", error);
		}
	}

}
