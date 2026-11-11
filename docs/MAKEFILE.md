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
| `make test` | Runs the project tests, including language tests when their toolchains are installed. |
| `make test-nolang` | Runs tests without the external-language execution tests. |
| `make jar` | Builds the runnable FAT JAR with `io.simpleide.App` as its main class. |
| `make run` | Packages the application, then launches its executable JAR. |

The executable JAR is created at
`simpleide/target/maven-mutation-testing-jar-with-dependencies.jar`. Run it
directly with:

```sh
java -jar simpleide/target/maven-mutation-testing-jar-with-dependencies.jar
```

`make run` keeps the command running while the application is open. Close the
application window to return to the terminal.
