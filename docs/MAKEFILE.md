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

The IDE JAR is created at `simpleide/target/simpleide.jar`. The standalone
Ambus distribution is created at `ambus/target/ambus-0.0.1-SNAPSHOT.zip`.
Tests remain in each Maven module.

`make run` keeps the command running while the application is open. Close the
application window to return to the terminal.
