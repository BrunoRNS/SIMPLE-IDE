/**
 * Workspace-wide text search.
 *
 * <p>
 * {@link io.simpleide.search.WorkspaceSearchService} walks the open folder, skips build
 * and version-control directories, ignores binary files and returns every matching line.
 * The service is blocking and is meant to run on a managed background node.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
package io.simpleide.search;
