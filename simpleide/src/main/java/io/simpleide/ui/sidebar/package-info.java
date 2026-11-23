/**
 * Left sidebar with the Explorer, Search and Settings views.
 *
 * <p>
 * {@link io.simpleide.ui.sidebar.ActivityBar} selects the view,
 * {@link io.simpleide.ui.sidebar.SidebarPanel} swaps the cards and
 * {@link io.simpleide.ui.sidebar.ExplorerPanel},
 * {@link io.simpleide.ui.sidebar.WorkspaceSearchPanel} and
 * {@link io.simpleide.ui.sidebar.SettingsPanel} implement the views. SVG variants are
 * loaded by {@link io.simpleide.ui.sidebar.icons.SidebarIconResources}. The frame owns
 * the visibility toggle, the folder state and the editor navigation; the Debug Console
 * belongs to the bottom output pane, not this package.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
package io.simpleide.ui.sidebar;
