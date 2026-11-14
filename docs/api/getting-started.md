# API getting started

This page adds Simple IDE to a Java project and runs a first integration. All
examples target Java 17 or newer.

## 1. Add the dependency

Choose one of the options in [Artifacts and imports](artifacts.md). The shortest
path is JitPack:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.BrunoRNS.SIMPLE-IDE</groupId>
  <artifactId>simpleide</artifactId>
  <version>v1.0.0beta</version>
</dependency>
```

Gradle:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.BrunoRNS.SIMPLE-IDE:simpleide:v1.0.0beta'
}
```

The `simpleide` artifact is the executable fat JAR. It already contains the
editor, LSP, terminal and language dependencies. You do not need to add
RSyntaxTextArea, FlatLaf, LSP4J or pty4j separately when you use this artifact.

## 2. Create a standalone editor

```java
import io.simpleide.api.Editors;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

public final class MinimalEditor {
  public static void main(String[] args) throws Exception {
    RSyntaxTextArea editor = Editors.open(Path.of("Example.java"));

    JFrame frame = new JFrame("Minimal Simple IDE editor");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new JScrollPane(editor));
    frame.setSize(900, 650);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}
```

This path does not start `ThreadMaster`, a language server or the terminal. It is
the smallest way to reuse the editor look and syntax mapping. See
[Standalone RSyntaxTextArea](rsyntax.md).

## 3. Run a source file

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Input;
import io.simpleide.language.io.Output;
import java.nio.file.Path;

public final class RunFile {
  public static void main(String[] args) throws Exception {
    AbstractLanguage language = Languages.start(Path.of("hello.py"));

    Input.getInstance().set("Ada");
    String line;
    while ((line = Output.getInstance().get(10)) != null) {
      System.out.println(line);
    }

    Languages.stop(language);
  }
}
```

`Languages.start` installs the available languages once, creates the language
instance, and registers it with the shared thread manager. The process output
and the optional input queue are global; do not run two interactive programs at
the same time unless you coordinate their input. See [Languages](languages.md).

## 4. Open an editor with LSP

```java
import io.simpleide.api.SimpleEditor;
import java.nio.file.Path;
import javax.swing.JFrame;

public final class EditorWithLsp {
  public static void main(String[] args) throws Exception {
    SimpleEditor editor = SimpleEditor.open(Path.of("src/main/java/Example.java"));
    editor.startLanguageServer().whenComplete((client, error) -> {
      if (error != null) {
        System.err.println("LSP unavailable: " + error.getMessage());
      }
    });

    JFrame frame = new JFrame("Editor with LSP");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.add(editor.component());
    frame.setSize(1100, 750);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosed(java.awt.event.WindowEvent event) {
        editor.close();
      }
    });
  }
}
```

The language server is installed under `~/.simpleide/lsp/` when needed. Opening
the editor does not block on the network; the future completes when the server
is initialized. See [LSP](lsp.md).

## 5. Add a background task

```java
import io.simpleide.api.Threads;

Threads.background(() -> {
  String result = expensiveOperation();
  Threads.onEdt(() -> statusLabel.setText(result));
});

Threads.background(() -> System.out.println("done"), () -> System.out.println("closed"));
```

`Threads.background` uses the shared worker pool. Keep tasks short and never
block the Swing EDT. See [Threading](threading.md).

## 6. Add a terminal

```java
import io.simpleide.api.Terminals;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;

TerminalPanel terminal = Terminals.panel();
BusyBoxTerminalController controller = Terminals.controller(terminal);
frame.add(terminal);
```

The embedded BusyBox binary is extracted from the fat JAR at runtime. The
controller creates a dedicated managed worker pool for terminal sessions. See
[Terminal](terminal.md).

## 7. Run the full IDE

```java
import io.simpleide.api.SimpleIDE;

public final class Launcher {
  public static void main(String[] args) {
    SimpleIDE.launch(args);
  }
}
```

This is equivalent to launching `io.simpleide.App` from the release JAR.

## Common mistakes

| Mistake | Consequence | Fix |
| --- | --- | --- |
| Updating Swing components from a background task | Random UI corruption or exceptions | Wrap updates in `Threads.onEdt(...)` |
| Forgetting `close()` on an editor with LSP | Server process stays alive | Close the editor or call `LanguageServerManager.close()` |
| Calling `ThreadMaster.shutdown()` in a library | The host application cannot schedule more tasks | Only shut down the manager you own |
| Running two interactive language processes | Input and output lines go to the wrong process | Serialize interactive runs or use separate JVMs |
| Adding only `simplebytecode-compiler` | No editor or LSP classes are available | Add the `simpleide` artifact |
| Using the thin module JAR without dependencies | `NoClassDefFoundError` at runtime | Use the fat JAR or declare the transitive dependencies |
