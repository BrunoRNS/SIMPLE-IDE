package io.simpleide.ui.treefile;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Watcher implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Watcher.class);

  private final Tree tree;
  private final WatchService service;
  private final Map<WatchKey, Path> keys = new HashMap<>();
  private final Map<Path, WatchKey> registeredPaths = new HashMap<>();
  private final CompletableFuture<Void> started = new CompletableFuture<>();
  private final AtomicReference<Path> pendingRoot = new AtomicReference<>();
  private volatile boolean closed;
  private Thread thread;

  public Watcher(Tree tree) throws IOException {
    this.tree = Objects.requireNonNull(tree, "tree");
    service = FileSystems.getDefault().newWatchService();
  }

  public synchronized CompletableFuture<Void> start(Path root) {
    if (thread != null) {
      throw new IllegalStateException("File watcher has already been started.");
    }
    if (closed) {
      throw new IllegalStateException("File watcher is closed.");
    }

    pendingRoot.set(validateRoot(root));
    thread = new Thread(this::watchLoop, "simpleide-file-watcher");
    thread.setDaemon(true);
    thread.start();
    return started;
  }

  public void setRoot(Path root) {
    Path normalizedRoot = validateRoot(root);
    Thread watcherThread;
    synchronized (this) {
      if (closed) {
        return;
      }
      if (thread == null) {
        start(normalizedRoot);
        return;
      }
      pendingRoot.set(normalizedRoot);
      watcherThread = thread;
    }
    watcherThread.interrupt();
  }

  private Path validateRoot(Path root) {
    Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    if (!Files.isDirectory(normalizedRoot)) {
      throw new IllegalArgumentException("Watch root is not a directory: " + normalizedRoot);
    }
    return normalizedRoot;
  }

  private void watchLoop() {
    while (!closed) {
      Path root = pendingRoot.getAndSet(null);
      if (root != null) {
        resetRegistrations();
        try {
          registerAll(root);
          started.complete(null);
        } catch (ClosedWatchServiceException error) {
          started.completeExceptionally(error);
          return;
        } catch (IOException error) {
          started.completeExceptionally(error);
          LOGGER.error("Unable to watch project directory {}.", root, error);
        }
      }

      try {
        processKey(service.take());
      } catch (InterruptedException error) {
        if (closed) {
          Thread.currentThread().interrupt();
          return;
        }
      } catch (ClosedWatchServiceException error) {
        started.completeExceptionally(error);
        return;
      }
    }
  }

  private void processKey(WatchKey key) {
    Path directory = keys.get(key);
    if (directory == null) {
      key.cancel();
      return;
    }

    boolean refreshDirectory = false;
    for (WatchEvent<?> event : key.pollEvents()) {
      if (event.kind() == OVERFLOW) {
        refreshDirectory = true;
        continue;
      }
      if (!(event.context() instanceof Path relativePath)) {
        continue;
      }

      Path changedPath = directory.resolve(relativePath).normalize();
      if (event.kind() == ENTRY_CREATE
          && Files.isDirectory(changedPath, LinkOption.NOFOLLOW_LINKS)) {
        try {
          registerAll(changedPath);
        } catch (IOException error) {
          LOGGER.warn("Unable to watch new directory {}.", changedPath, error);
        }
      } else if (event.kind() == ENTRY_DELETE) {
        cancelSubtree(changedPath);
      }
      if (event.kind() == ENTRY_CREATE || event.kind() == ENTRY_DELETE) {
        refreshDirectory = true;
      }
    }

    if (!key.reset()) {
      keys.remove(key);
      registeredPaths.remove(directory, key);
    }
    if (refreshDirectory) {
      try {
        tree.refreshDirectory(directory);
      } catch (IllegalStateException error) {
        if (!closed) {
          LOGGER.error("Unable to refresh the tree for {}.", directory, error);
        }
      }
    }
  }

  private void registerAll(Path root) throws IOException {
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
          throws IOException {
        register(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private void register(Path path) throws IOException {
    Path normalizedPath = path.toAbsolutePath().normalize();
    if (registeredPaths.containsKey(normalizedPath)) {
      return;
    }
    WatchKey key = normalizedPath.register(service, ENTRY_CREATE, ENTRY_DELETE);
    registeredPaths.put(normalizedPath, key);
    keys.put(key, normalizedPath);
  }

  private void cancelSubtree(Path path) {
    Iterator<Map.Entry<Path, WatchKey>> iterator = registeredPaths.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Path, WatchKey> entry = iterator.next();
      if (entry.getKey().startsWith(path)) {
        entry.getValue().cancel();
        keys.remove(entry.getValue());
        iterator.remove();
      }
    }
  }

  private void resetRegistrations() {
    for (WatchKey key : keys.keySet()) {
      key.cancel();
    }
    keys.clear();
    registeredPaths.clear();
  }

  @Override
  public void close() {
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
    }
    started.completeExceptionally(new IllegalStateException("File watcher was closed before starting."));
    try {
      service.close();
    } catch (IOException error) {
      LOGGER.warn("Unable to close file watcher.", error);
    }
    Thread watcherThread = thread;
    if (watcherThread != null) {
      watcherThread.interrupt();
    }
  }
}
