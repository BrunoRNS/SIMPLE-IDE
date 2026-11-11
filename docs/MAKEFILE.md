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
| `make build` | Compiles the Maven project without running tests. |
| `make test` | Runs the project's tests. |
| `make jar` | Packages the application and prints the output directory. |
| `make run` | Packages the application, then launches its executable JAR. |

The executable JAR is created at
`simpleide/target/maven-mutation-testing-jar-with-dependencies.jar`.

`make run` keeps the command running while the application is open. Close the
application window to return to the terminal. It enables Java preview features
for the Java 21 macOS ARM64 PTY runtime.
