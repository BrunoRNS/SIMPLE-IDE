ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif

APP_JAR := simpleide/target/simpleide.jar
AMBUS_ZIP := ambus/target/ambus-0.0.1-SNAPSHOT.zip
AMBUS_HOME := $(CURDIR)/ambus/target/lsp_configurado

.PHONY: build test test-nolang run jar

build:
	$(MVNW) -B -DskipTests package
	@echo IDE generated: $(APP_JAR)
	@echo Ambus generated: $(AMBUS_ZIP)

test:
	$(MVNW) -B test

test-nolang:
	$(MVNW) -B -DexcludedGroups=language-tools test

run: jar
	java --enable-preview -jar $(APP_JAR)

clean:
	$(MVNW) -B clean
jar:
	$(MVNW) -B -DskipTests package
	@echo Executable FAT JAR generated: $(APP_JAR)
