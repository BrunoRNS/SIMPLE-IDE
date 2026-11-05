package io.simpleide.utils.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.simpleide.ui.treefile.Directory;
import io.simpleide.ui.treefile.File;
import io.simpleide.ui.treefile.TreeFile;

class FileServiceTest {

  @TempDir
  Path root;

  private final FileService fileService = new FileService();

  @Test
  void createsFileUnderParentDirectory() throws IOException {
    Directory parent = new Directory(root);

    TreeFile created = fileService.createNewFile(parent, "Main.java", false);

    assertThat(created).isInstanceOf(File.class);
    assertThat(created.getPath()).isEqualTo(root.resolve("Main.java"));
    assertThat(Files.exists(created.getPath())).isTrue();
  }

  @Test
  void createsDirectoryUnderParentDirectory() throws IOException {
    Directory parent = new Directory(root);

    TreeFile created = fileService.createNewFile(parent, "src", true);

    assertThat(created).isInstanceOf(Directory.class);
    assertThat(created.getPath()).isEqualTo(root.resolve("src"));
    assertThat(Files.isDirectory(created.getPath())).isTrue();
  }

  @Test
  void rejectsNamesThatEscapeParentDirectory() throws IOException {
    Directory parent = new Directory(root);
    String outsideName = root.getFileName() + "-outside.txt";

    assertThatThrownBy(() -> fileService.createNewFile(parent, "../" + outsideName, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(root.getParent().resolve(outsideName))).isFalse();
  }

  @Test
  void rejectsBlankCreationName() throws IOException {
    Directory parent = new Directory(root);

    assertThatThrownBy(() -> fileService.createNewFile(parent, "  ", false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(root.resolve("  "))).isFalse();
  }

  @Test
  void renamesFileAndReturnsNewPath() throws IOException {
    Path original = Files.createFile(root.resolve("before.txt"));

    Path renamed = fileService.renameFile(original, "after.txt");

    assertThat(renamed).isEqualTo(root.resolve("after.txt"));
    assertThat(Files.exists(original)).isFalse();
    assertThat(Files.exists(renamed)).isTrue();
  }

  @Test
  void rejectsRenameNamesThatEscapeParentDirectory() throws IOException {
    Path original = Files.createFile(root.resolve("before.txt"));

    assertThatThrownBy(() -> fileService.renameFile(original, "../outside.txt"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(original)).isTrue();
    assertThat(Files.exists(root.getParent().resolve("outside.txt"))).isFalse();
  }

  @Test
  void rejectsBlankRenameName() throws IOException {
    Path original = Files.createFile(root.resolve("before.txt"));

    assertThatThrownBy(() -> fileService.renameFile(original, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Files.exists(original)).isTrue();
  }

  @Test
  void doesNotOverwriteExistingFileWhenRenaming() throws IOException {
    Path original = Files.writeString(root.resolve("before.txt"), "original");
    Path existing = Files.writeString(root.resolve("after.txt"), "existing");

    assertThatThrownBy(() -> fileService.renameFile(original, "after.txt"))
        .isInstanceOf(IOException.class);

    assertThat(Files.readString(original)).isEqualTo("original");
    assertThat(Files.readString(existing)).isEqualTo("existing");
  }

  @Test
  void deletesFile() throws IOException {
    Path file = Files.createFile(root.resolve("remove.txt"));

    fileService.deleteFile(file);

    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void deletesDirectoryAndItsContents() throws IOException {
    Path directory = Files.createDirectory(root.resolve("remove"));
    Path nestedDirectory = Files.createDirectory(directory.resolve("nested"));
    Path file = Files.createFile(nestedDirectory.resolve("remove.txt"));

    fileService.deleteFile(directory);

    assertThat(Files.exists(directory)).isFalse();
    assertThat(Files.exists(nestedDirectory)).isFalse();
    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void deletingMissingPathDoesNothing() throws IOException {
    Path missing = root.resolve("missing.txt");

    fileService.deleteFile(missing);

    assertThat(Files.exists(missing)).isFalse();
  }
}
