package io.simpleide.frame.layout;

import java.awt.FlowLayout;

import javax.swing.JComponent;

/**
 * Flow layout with convenience methods for alignment-specific component insertion.
 *
 * <p>
 * Each helper changes the current alignment before adding the component, so components
 * added later continue to use the last selected alignment.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class SimpleLayout extends FlowLayout {

	/**
	 * Creates a flow layout with the default alignment and spacing.
	 */
	public SimpleLayout() {
		super();
	}

	/**
	 * Adds a component using left alignment.
	 * @param comp component to add
	 */
	public void addLeftComponent(JComponent comp) {
		this.setAlignment(FlowLayout.LEFT);
		this.addLayoutComponent(null, comp);
	}

	/**
	 * Adds a component using right alignment.
	 * @param comp component to add
	 */
	public void addRightComponent(JComponent comp) {
		this.setAlignment(FlowLayout.RIGHT);
		this.addLayoutComponent(null, comp);
	}

	/**
	 * Adds a component using center alignment.
	 * @param comp component to add
	 */
	public void addCenterComponent(JComponent comp) {
		this.setAlignment(FlowLayout.CENTER);
		this.addLayoutComponent(null, comp);
	}

}
