package io.simpleide.lsp.core;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocumentManager {
  private final Map<String, Document> documents = new HashMap<>();

  public Document open(Path path, String languageId) {

    String uri = path.toAbsolutePath().normalize().toUri().toString();

    return documents.computeIfAbsent(uri, key -> new Document(path, languageId));
  }

  public Document getDocument(String uri) {
    return documents.get(uri);
  }

  public void close(String uri) {
    documents.remove(uri);
  }

  public Collection<Document> getOpenDocuments() {
    return Collections.unmodifiableCollection(documents.values());
  }
}
