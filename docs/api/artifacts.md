# Artifacts and imports

Simple IDE publishes source and a multi-module Maven build. Consumers usually
want one of these artifacts:

| Artifact | Content | Use when |
| --- | --- | --- |
| `simpleide` fat JAR | Application classes, resources and all runtime dependencies | Embedding any Simple IDE component |
| `simplebytecode-compiler` | Java, Kotlin and Groovy in-memory compiler/runner | Using only the bytecode compiler |
| `simplebytecode-compiler` `jar-with-dependencies` | Compiler plus Groovy/Kotlin dependencies | Running the compiler standalone |

## Release JAR

Every GitHub release contains:

```text
simpleide-v<version>-any-with-deps.jar
```

The JAR is executable and can also be placed on a classpath:

```sh
java -jar simpleide-v1.0.0beta-any-with-deps.jar
```

For a Maven project, install it into the local repository once:

```sh
mvn install:install-file \
  -Dfile=simpleide-v1.0.0beta-any-with-deps.jar \
  -DgroupId=io.simpleide \
  -DartifactId=simpleide \
  -Dversion=1.0.0beta \
  -Dpackaging=jar
```

Then depend on it:

```xml
<dependency>
  <groupId>io.simpleide</groupId>
  <artifactId>simpleide</artifactId>
  <version>1.0.0beta</version>
</dependency>
```

Gradle:

```groovy
dependencies {
    implementation files('libs/simpleide-v1.0.0beta-any-with-deps.jar')
}
```

## JitPack

JitPack builds the repository from a tag, branch or commit. This is useful when
you want to consume a specific development revision without publishing to Maven
Central.

Add the repository:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

Depend on the application module:

```xml
<dependency>
  <groupId>com.github.BrunoRNS.SIMPLE-IDE</groupId>
  <artifactId>simpleide</artifactId>
  <version>v1.0.0beta</version>
</dependency>
```

Depend on the bytecode compiler module only:

```xml
<dependency>
  <groupId>com.github.BrunoRNS.SIMPLE-IDE</groupId>
  <artifactId>simplebytecode-compiler</artifactId>
  <version>v1.0.0beta</version>
</dependency>
```

The bytecode compiler standalone executable classifier is:

```xml
<classifier>jar-with-dependencies</classifier>
```

Gradle examples:

```groovy
dependencies {
    implementation 'com.github.BrunoRNS.SIMPLE-IDE:simpleide:v1.0.0beta'
    implementation 'com.github.BrunoRNS.SIMPLE-IDE:simplebytecode-compiler:v1.0.0beta'
}
```

### JitPack version selectors

| Selector | Meaning |
| --- | --- |
| `v1.0.0beta` | The tagged release |
| `main` | Latest commit on the stable branch |
| `develop` | Latest commit on the development branch |
| `<commit-sha>` | Exact revision |

Prefer tags for reproducible builds. Branches move.

### If JitPack does not resolve the module

JitPack creates artifacts from the root `pom.xml` and its modules. If the module
coordinate is not available:

1. Open the JitPack build log for the tag.
2. Confirm that the `simpleide` module was built successfully.
3. Use the root coordinate `com.github.BrunoRNS.SIMPLE-IDE:SIMPLE-IDE:v1.0.0beta`
   or download the release fat JAR and install it manually.
4. Pin the dependency to a released tag instead of a branch.

## Maven coordinates from the reactor

The reactor modules use group `io` and version `1.0.0beta`:

| Module | Coordinate |
| --- | --- |
| Parent | `io:simpleide-parent:1.0.0beta` |
| Application | `io:simpleide:1.0.0beta` |
| Compiler | `io:simplebytecode-compiler:1.0.0beta` |

These coordinates are useful when building the project locally with
`mvn install`. They are not published to Maven Central.

## Isolated imports

The fat JAR contains every subsystem, but using one subsystem does not start the
others. The table below shows the entry point and the effects.

| Goal | Entry point | Starts processes? | Starts Swing? |
| --- | --- | --- | --- |
| RSyntaxTextArea only | `new io.simpleide.ui.editor.Editor()` or `Editors.create()` | No | Creates a Swing component |
| Editor tab only | `SimpleEditor.open(path)` | No, unless LSP is requested | Yes |
| LSP only | `LanguageServerManager` + `LspClient` | Yes | No |
| Threads only | `Threads.master()` | Yes, worker threads | No application frame |
| Languages only | `Languages.start(path)` | Yes | No |
| Terminal only | `Terminals.panel()` + controller | Yes | Yes |
| Full application | `SimpleIDE.launch()` | Yes | Yes |

See [Isolated modules](isolated-modules.md) for exact imports and caveats.

## Only RSyntaxTextArea

If you only need the editor widget and do not want any other Simple IDE code,
you can depend directly on RSyntaxTextArea:

```xml
<dependency>
  <groupId>com.fifesoft</groupId>
  <artifactId>rsyntaxtextarea</artifactId>
  <version>3.6.3</version>
</dependency>
```

Use Simple IDE's `Editor` subclass only when you want its default font, folding,
auto-indent, bracket matching and file-type syntax mapping:

```java
Editor editor = new Editor();
editor.setSyntaxStyle(TypeFile.JAVA);
```

## Licensing when redistributing

- Simple IDE code is BSD 3-Clause.
- The fat JAR contains dependencies with their own licenses, including LGPL,
  EPL and Apache 2.0 components. See [Third-party licenses](../third-party/LICENSES.md).
- Embedded BusyBox binaries are GPLv2 programs, not linked Java code. The
  corresponding source archives are bundled in the JAR resources. If you
  redistribute the fat JAR or a jpackage image, keep those resources and
  licenses intact.
