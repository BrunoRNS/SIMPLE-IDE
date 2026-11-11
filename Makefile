ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif

APP_JAR := simpleide/target/maven-mutation-testing-jar-with-dependencies.jar

.PHONY: build test run jar

build:
	$(MVNW) -B compile

test:
	$(MVNW) -B test

run: jar
	java --enable-preview -jar $(APP_JAR)

jar:
	$(MVNW) -B -DskipTests package
	@echo JAR generated: simpleide/target
