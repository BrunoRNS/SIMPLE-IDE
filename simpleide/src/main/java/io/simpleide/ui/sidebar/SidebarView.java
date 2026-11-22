package io.simpleide.ui.sidebar;

/**
 * Views hosted in the left sidebar.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
public enum SidebarView {

	/**
	 * Project folder tree.
	 */
	EXPLORER("Explorer"),

	/**
	 * Workspace-wide text search.
	 */
	SEARCH("Search"),

	/**
	 * Persistent application settings.
	 */
	SETTINGS("Settings");

	private final String title;

	SidebarView(String title) {
		this.title = title;
	}

	/**
	 * Returns the title shown above the view.
	 * @return the human readable title
	 */
	public String getTitle() {
		return title;
	}

}
