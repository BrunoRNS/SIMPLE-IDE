package io.simpleide.lsp.core;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the documents currently open in an LSP session, keyed by URI.
 *
 * <p>
 * Opening a document reuses the existing entry for the same normalized URI so the version
 * and language id stay consistent. This manager is not synchronized and is intended for
 * use from a single owner, typically the editor integration.
 *
 * @author LuisOtavio13
 * @since v1.0.0beta
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/lsp.md">docs/api/lsp.md</a>
 */
public class DocumentManager {

	private final Map<String, Document> documents = new HashMap<>();

	/**
	 * Opens a document, reusing the entry registered for its URI.
	 * @param path document path
	 * @param languageId LSP language identifier used when the document is new
	 * @return the existing or newly created document
	 */
	public Document open(Path path, String languageId) {

		String uri = path.toAbsolutePath().normalize().toUri().toString();

		return documents.computeIfAbsent(uri, key -> new Document(path, languageId));
	}

	/**
	 * Returns the document registered for a URI.
	 * @param uri document URI
	 * @return the document, or {@code null} when it is not open
	 */
	public Document getDocument(String uri) {
		return documents.get(uri);
	}

	/**
	 * Removes a document from the open set.
	 * @param uri document URI
	 */
	public void close(String uri) {
		documents.remove(uri);
	}

	/**
	 * Returns an unmodifiable view of the open documents.
	 * @return the open documents
	 */
	public Collection<Document> getOpenDocuments() {
		return Collections.unmodifiableCollection(documents.values());
	}

}
