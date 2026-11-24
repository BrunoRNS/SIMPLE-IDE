ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
PKG_RUNNER := powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package.ps1
PYTHON := python
else
MVNW := ./mvnw
PKG_RUNNER := sh scripts/package.sh
PYTHON := python3
endif

APP_JAR := simpleide/target/simpleide.jar
PKG_DIR := simpleide/target/pkg
VERSION := $(shell sed -n 's/^version=//p' simpleide/src/main/resources/simpleide.properties 2>/dev/null | tr -d '\r')
JAVA_OPTS ?= -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED

# Audio/video playback is optional. scripts/javafx-version.sh selects the
# nojavafx, javafx22 or javafx23 variant from the current JDK and platform.
# Override it with SIMPLEIDE_MEDIA_VARIANT=nojavafx|javafx22|javafx23. Linux
# arm64 always resolves to nojavafx. See docs/reference/JAVAFX-MEDIA.md.
MEDIA_VARIANT := $(shell sh scripts/javafx-version.sh)
ifeq ($(filter $(MEDIA_VARIANT),nojavafx javafx22 javafx23),)
$(error scripts/javafx-version.sh returned an unsupported variant: $(MEDIA_VARIANT))
endif
export SIMPLEIDE_PKG_VARIANT := $(MEDIA_VARIANT)
MVN := $(MVNW) -Dsimpleide.media.variant=$(MEDIA_VARIANT)

.PHONY: all build test test-nolang test-consumer jar pkg pkg-verify change-version run clean format check-format format-check verify help

all: test jar

# Compile every module without running tests or packaging artifacts.
build:
	$(MVN) -B -DskipTests compile

# Run the complete test suite.
test: check-format
	$(MVN) -B test

# Run the test suite without tests that require external language toolchains.
test-nolang: check-format
	$(MVN) -B -DexcludedGroups=language-tools test

# Install the reactor locally, then resolve a standalone consumer POM once per
# media variant and verify the transitive JavaFX dependencies. This validates
# the consumer-visible profile contract, including OS/architecture gating.
test-consumer:
	$(MVN) -B -DskipTests install
	$(PYTHON) scripts/ci/test-consumer-profiles.py --skip-install

# Package the executable fat JAR. Tests are intentionally not part of this target.
jar:
	$(MVN) -B -DskipTests package
	@echo "Fat JAR: $(APP_JAR)"

# Build a self-contained jpackage application image for the current OS/arch.
# Override with SIMPLEIDE_PKG_TYPE=deb|rpm|dmg|exe|msi|app-image.
# The Windows .msi is built for x64 only.
pkg: jar
	$(PKG_RUNNER)

# Validates the packages produced by `make pkg` (zip, installers). Used by CI
# so a broken executable never reaches a release.
ifeq ($(OS),Windows_NT)
PKG_VERIFY := powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-package.ps1
else
PKG_VERIFY := sh scripts/validate-package.sh
endif

pkg-verify: pkg
	$(PKG_VERIFY)

# Changes the project version everywhere it is declared (pom files and the
# application properties). Requires confirmation; the default answer is NO.
change-version:
	@sh scripts/change-version.sh "$(VERSAO)"

# Launch the executable fat JAR.
run: jar
	java $(JAVA_OPTS) -jar "$(APP_JAR)"

# Apply Spring Java Format and the repository's import ordering to every Java source.
format:
	$(MVN) -B spring-javaformat:apply
	java scripts/JavaImportFormatter.java --apply

# Fail when Java sources violate Spring Java Format or the repository's import ordering.
check-format:
	$(MVN) -B spring-javaformat:validate
	java scripts/JavaImportFormatter.java --check

# Backwards-compatible alias for check-format.
format-check: check-format

# Full local validation: formatting, tests, consumer resolution and packaging.
verify: check-format test test-consumer jar

clean:
	$(MVN) -B clean
	rm -rf "$(PKG_DIR)"

help:
	@echo "Simple IDE build targets"
	@echo ""
	@echo "  make build         Compile all modules (no tests, no packages)"
	@echo "  make test          Run the full test suite"
	@echo "  make test-nolang   Run tests without external language toolchains"
	@echo "  make test-consumer Validate the Maven consumer media variants"
	@echo "  make jar           Package the executable fat JAR"
	@echo "  make pkg           Build every release package for this OS/architecture"
	@echo "  make pkg-verify    Build packages and validate the produced executables"
	@echo "  make change-version VERSION"
	@echo "                     Change the project version (asks for confirmation)"
	@echo "  make run           Launch the executable fat JAR"
	@echo "  make format        Apply Java source and import formatting"
	@echo "  make check-format  Check Java source and import formatting"
	@echo "  make format-check  Alias for check-format"
	@echo "  make verify        Check formatting, run tests and package"
	@echo "  make clean         Remove generated build and package outputs"
	@echo ""
	@echo "Version: $(VERSION)"
	@echo "Media variant: $(MEDIA_VARIANT)"
	@echo "Fat JAR: $(APP_JAR)"
	@echo "Package output: $(PKG_DIR)"
