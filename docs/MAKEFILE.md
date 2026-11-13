# Building and Running with Make

The repository includes a root-level `Makefile` for common build tasks. It uses
the Maven Wrapper included in the repository, so a separate Maven installation
is not required.

## Requirements

- Java Development Kit (JDK) 17 or later
- GNU Make

GNU Make is available through the build tools on many Linux and macOS systems.
On Windows, install GNU Make and ensure the `make` command is available on your
`PATH`. The Makefile selects `mvnw.cmd` on Windows and `./mvnw` on macOS and
Linux.

Run the commands below from the repository root.

## Commands

| Command | Description |
| --- | --- |
| `make all` | Runs the tests, then packages the IDE and Ambus. |
| `make build` | Packages the IDE and the separate Ambus Java LSP without running tests. |
| `make test` | Runs the project's tests. |
| `make jar` | Alias for `make build`. |
| `make run` | Packages both artifacts, then launches the IDE configured to use the local Ambus build. |
| `make clean` | Removes generated Maven build outputs. |
| `make test-nolang` | Runs tests without the external-language execution tests. |


The executable JAR is created at
`simpleide/target/maven-mutation-testing-jar-with-dependencies.jar`. Run it
directly with:

```sh
java -jar simpleide/target/maven-mutation-testing-jar-with-dependencies.jar
```

`make run` keeps the command running while the application is open. Close the
application window to return to the terminal. It enables Java preview features
for the Java 21 macOS ARM64 PTY runtime.
