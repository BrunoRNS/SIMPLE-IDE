package io.simpleide.ui.sidebar;

import java.awt.Color;
import java.util.Objects;

/**
 * A graph lane travelling between two rows, identified by the commit that will occupy it.
 *
 * @param id hash of the commit the lane is heading to
 * @param color lane color
 * @author LuisOtavio13
 * @since v1.1.0
 */
record GitGraphLane(String id, Color color) {

	GitGraphLane {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(color, "color");
	}

}
