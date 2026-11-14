# API examples

Complete, copy-paste examples. Each file is a separate `main` class.

## 1. Editor with local completion and LSP

`EditorWindow.java`:

```java
import io.simpleide.api.SimpleEditor;
import io.simpleide.lsp.LspStatus;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JLabel;

public final class EditorWindow {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: EditorWindow <source-file>");
      System.exit(2);
    }

    JLabel status = new JLabel("Ready");
    SimpleEditor editor =
        SimpleEditor.open(
            Path.of(args[0]),
            (LspStatus event) ->
                javax.swing.SwingUtilities.invokeLater(
                    () -> status.setText(event.serverName() + ": " + event.state())));

    JFrame frame = new JFrame("Simple IDE editor");
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.setLayout(new BorderLayout());
    frame.add(editor.component(), BorderLayout.CENTER);
    frame.add(status, BorderLayout.SOUTH);
    frame.setSize(1100, 750);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            editor.close();
          }
        });

    editor
        .startLanguageServer()
        .exceptionally(
            error -> {
              status.setText("LSP unavailable: " + error.getMessage());
              return null;
            });
  }
}
```

Build and run:

```sh
javac -cp simpleide-v1.0.0beta-any-with-deps.jar EditorWindow.java
java -cp .:simpleide-v1.0.0beta-any-with-deps.jar EditorWindow src/main/java/Example.java
```

On Windows use `;` as the classpath separator.

## 2. Run a source file and stream its output

`RunSource.java`:

```java
import io.simpleide.api.Languages;
import io.simpleide.language.AbstractLanguage;
import io.simpleide.language.io.Output;
import java.nio.file.Path;

public final class RunSource {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: RunSource <source-file>");
      System.exit(2);
    }

    AbstractLanguage language = Languages.start(Path.of(args[0]));
    Runtime.getRuntime().addShutdownHook(new Thread(() -> Languages.stop(language)));

    String line;
    while ((line = Output.getInstance().get(30)) != null) {
      System.out.println(line);
    }
    Languages.stop(language);
  }
}
```

The `Output` queue is shared. Do not run two instances of this class in the same
JVM unless you accept interleaved output.

## 3. Background HTTP request with an EDT update

`BackgroundDemo.java`:

```java
import io.simpleide.api.Threads;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.swing.JFrame;
import javax.swing.JLabel;

public final class BackgroundDemo {
  public static void main(String[] args) {
    JLabel label = new JLabel("Loading...");
    JFrame frame = new JFrame("Background demo");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(label);
    frame.setSize(500, 200);
    frame.setVisible(true);

    Threads.background(
        () -> {
          try {
            HttpResponse<String> response =
                HttpClient.newHttpClient()
                    .send(
                        HttpRequest.newBuilder(URI.create("https://example.com")).build(),
                        HttpResponse.BodyHandlers.ofString());
            Threads.onEdt(() -> label.setText("HTTP " + response.statusCode()));
          } catch (Exception error) {
            Threads.onEdt(() -> label.setText("Failed: " + error.getMessage()));
          }
        });
  }
}
```

## 4. Terminal in a window

`TerminalWindow.java`:

```java
import io.simpleide.api.Terminals;
import io.simpleide.ui.terminal.BusyBoxTerminalController;
import io.simpleide.ui.terminal.TerminalPanel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

public final class TerminalWindow {
  public static void main(String[] args) {
    if (!Terminals.isSupported()) {
      System.err.println("The embedded terminal is not supported on this platform.");
      System.exit(1);
    }

    TerminalPanel panel = Terminals.panel();
    BusyBoxTerminalController controller = Terminals.controller(panel);

    JFrame frame = new JFrame("Simple IDE terminal");
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.add(panel);
    frame.setSize(900, 500);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent event) {
            controller.onWindowClosing(frame::dispose);
          }
        });
  }
}
```

## 5. Read-only code viewer

`CodeViewer.java`:

```java
import io.simpleide.api.Editors;
import io.simpleide.ui.editor.Editor;
import java.nio.file.Path;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

public final class CodeViewer {
  public static void main(String[] args) throws Exception {
    Editor editor = Editors.open(Path.of(args[0]));
    editor.setEditable(false);
    editor.setLineWrap(false);

    JFrame frame = new JFrame(args[0]);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new JScrollPane(editor));
    frame.setSize(1000, 700);
    frame.setVisible(true);
  }
}
```

## Maven project example

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>example</groupId>
  <artifactId>simple-ide-embed</artifactId>
  <version>1.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <repositories>
    <repository>
      <id>jitpack.io</id>
      <url>https://jitpack.io</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>com.github.BrunoRNS.SIMPLE-IDE</groupId>
      <artifactId>simpleide</artifactId>
      <version>v1.0.0beta</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```

Place a `main` class under `src/main/java`, then run:

```sh
mvn -q compile exec:java -Dexec.mainClass=example.EditorWindow -Dexec.args=README.md
```

The `exec-maven-plugin` is not declared above; add it or run the class from your
IDE with the dependency classpath.
