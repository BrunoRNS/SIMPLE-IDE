ifeq ($(OS),Windows_NT)
MVNW := mvnw.cmd
else
MVNW := ./mvnw
endif

APP_JAR := simpleide/target/simpleide.jar
AMBUS_ZIP := ambus/target/ambus-0.0.1-SNAPSHOT.zip
AMBUS_HOME := $(CURDIR)/ambus/target/lsp_configurado

.PHONY: build test run jar all clean

all: build test

build:
	$(MVNW) -B -DskipTests package
	@echo IDE generated: $(APP_JAR)
	@echo Ambus generated: $(AMBUS_ZIP)

test:
	$(MVNW) -B test

run: jar
	java -Dsimpleide.lsp.ambus.home="$(AMBUS_HOME)" -jar "$(APP_JAR)"

jar: build

clean:
	$(MVNW) -B clean
