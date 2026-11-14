ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
PKG_RUNNER := powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package.ps1
else
MVNW := ./mvnw
PKG_RUNNER := sh scripts/package.sh
endif

APP_JAR := simpleide/target/simpleide.jar
PKG_DIR := simpleide/target/pkg
VERSION := $(shell sed -n 's/^version=//p' simpleide/src/main/resources/simpleide.properties 2>/dev/null | tr -d '\r')
JAVA_OPTS ?= -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED

.PHONY: all build test test-nolang jar pkg pkg-verify change-version run clean format format-check verify help

all: test jar

# Compile every module without running tests or packaging artifacts.
build:
	$(MVNW) -B -DskipTests compile

# Run the complete test suite.
test:
	$(MVNW) -B test

# Run the test suite without tests that require external language toolchains.
test-nolang:
	$(MVNW) -B -DexcludedGroups=language-tools test

# Package the executable fat JAR. Tests are intentionally not part of this target.
jar:
	$(MVNW) -B -DskipTests package
	@echo "Fat JAR: $(APP_JAR)"

# Build a self-contained jpackage application image for the current OS/arch.
# Override with SIMPLEIDE_PKG_TYPE=deb|rpm|dmg|exe|msi|app-image.
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

# Apply the project Java formatter.
format:
	$(MVNW) -B spotless:apply

# Fail when any Java source is not formatted according to the project style.
format-check:
	$(MVNW) -B spotless:check

# Full local validation: formatting, tests and packaging.
verify: format-check test jar

clean:
	$(MVNW) -B clean
	rm -rf "$(PKG_DIR)"

help:
	@echo "Simple IDE build targets"
	@echo ""
	@echo "  make build         Compile all modules (no tests, no packages)"
	@echo "  make test          Run the full test suite"
	@echo "  make test-nolang   Run tests without external language toolchains"
	@echo "  make jar           Package the executable fat JAR"
	@echo "  make pkg           Build every release package for this OS/architecture"
	@echo "  make pkg-verify    Build packages and validate the produced executables"
	@echo "  make change-version VERSION"
	@echo "                     Change the project version (asks for confirmation)"
	@echo "  make run           Launch the executable fat JAR"
	@echo "  make format        Apply the standard Java formatter"
	@echo "  make format-check  Check formatting without changing files"
	@echo "  make verify        Check formatting, run tests and package"
	@echo "  make clean         Remove generated build and package outputs"
	@echo ""
	@echo "Version: $(VERSION)"
	@echo "Fat JAR: $(APP_JAR)"
	@echo "Package output: $(PKG_DIR)"
