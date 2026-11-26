package io.simpleide.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 * Resolves the Git repository associated with an opened folder.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public final class GitRepositoryResolver {

	private GitRepositoryResolver() {
	}

	/**
	 * Finds the repository containing or above the supplied folder.
	 * @param openedFolder folder selected in the Explorer
	 * @return repository work tree, or empty when the folder is outside a repository
	 */
	public static Optional<Path> resolve(Path openedFolder) {
		if (openedFolder == null || !Files.isDirectory(openedFolder)) {
			return Optional.empty();
		}

		Path start = openedFolder.toAbsolutePath().normalize();
		try (Repository repository = new FileRepositoryBuilder().findGitDir(start.toFile())
			.setMustExist(true)
			.build()) {
			Path workTree = repository.getWorkTree() == null ? null
					: repository.getWorkTree().toPath().toAbsolutePath().normalize();
			return Optional.ofNullable(workTree);
		}
		catch (IllegalArgumentException | IOException exception) {
			return Optional.empty();
		}
	}

}
