ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif

APP_JAR := simpleide/target/maven-mutation-testing-jar-with-dependencies.jar

.PHONY: build test test-nolang run jar

build:
	$(MVNW) -B compile

test:
	$(MVNW) -B test

test-nolang:
	$(MVNW) -B -DexcludedGroups=language-tools test

run: jar
	java -jar $(APP_JAR)

jar:
	$(MVNW) -B -DskipTests package
	@echo Executable FAT JAR generated: $(APP_JAR)
