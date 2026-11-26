package io.simpleide.frame.layout;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JComponent;

/**
 * Grid bag layout with convenience methods for the main application frame.
 *
 * <p>
 * Components are placed through short {@code addComponent} overloads that fill both
 * directions and optionally define growth weights.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/ui/application.md">docs/api/ui/application.md</a>
 * @author LuisOtavio13
 * @since v1.0.0beta
 */
public class MainLayout extends GridBagLayout {

	/**
	 * Creates an empty grid bag layout.
	 */
	public MainLayout() {
		super();
	}

	/**
	 * Adds a component with full grid, size and weight constraints.
	 * @param component component to add
	 * @param gridx grid column
	 * @param gridy grid row
	 * @param gridwidth number of columns spanned
	 * @param gridheight number of rows spanned
	 * @param weightx horizontal growth weight
	 * @param weighty vertical growth weight
	 */
	public void addComponent(JComponent component, int gridx, int gridy, int gridwidth, int gridheight, double weightx,
			double weighty) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = gridx;
		gbc.gridy = gridy;
		gbc.gridwidth = gridwidth;
		gbc.gridheight = gridheight;
		gbc.weightx = weightx;
		gbc.weighty = weighty;
		gbc.fill = GridBagConstraints.BOTH;
		this.setConstraints(component, gbc);
	}

	/**
	 * Adds a component without growth weight.
	 * @param component component to add
	 * @param gridx grid column
	 * @param gridy grid row
	 * @param gridwidth number of columns spanned
	 * @param gridheight number of rows spanned
	 */
	public void addComponent(JComponent component, int gridx, int gridy, int gridwidth, int gridheight) {
		this.addComponent(component, gridx, gridy, gridwidth, gridheight, 0.0, 0.0);
	}

	/**
	 * Adds a component that occupies a single cell.
	 * @param component component to add
	 * @param gridx grid column
	 * @param gridy grid row
	 */
	public void addComponent(JComponent component, int gridx, int gridy) {
		this.addComponent(component, gridx, gridy, 1, 1);
	}

	/**
	 * Adds a single-cell component with growth weights.
	 * @param component component to add
	 * @param gridx grid column
	 * @param gridy grid row
	 * @param weightx horizontal growth weight
	 * @param weighty vertical growth weight
	 */
	public void addComponent(JComponent component, int gridx, int gridy, double weightx, double weighty) {
		this.addComponent(component, gridx, gridy, 1, 1, weightx, weighty);
	}

	/**
	 * Removes a component from this layout.
	 * @param component component to remove
	 */
	public void removeComponent(JComponent component) {
		this.removeLayoutComponent(component);
	}

	/**
	 * Removes all layout constraints from this layout.
	 */
	public void removeAllComponents() {
		this.removeLayoutComponent(null);
	}

	/**
	 * Invalidates the cached layout information.
	 */
	public void updateLayout() {
		this.invalidateLayout(null);
	}

	/**
	 * Invalidates the layout and immediately lays out the container.
	 */
	public void refreshLayout() {
		this.updateLayout();
		this.layoutContainer(null);
	}

}
