package io.simpleide.ui.editor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests the welcome screen's centered, theme-aware application mark. */
class EditorWelcomeScreenTest {

	@Test
	void logoScalesProportionallyToFillTheSpaceAboveTheActions() {
		JPanel welcome = EditorWelcomeScreen.create(() -> {
		}, () -> {
		});
		List<JComponent> components = new ArrayList<>();
		collectComponents(welcome, components);
		JComponent logo = components.stream()
			.filter(component -> "welcome-logo".equals(component.getName()))
			.findFirst()
			.orElseThrow();
		assertThat(logo.getPreferredSize().width).isEqualTo(220);
		assertThat(logo.getPreferredSize().height).isEqualTo(220);
		logo.setSize(360, 300);
		BufferedImage rendered = new BufferedImage(360, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = rendered.createGraphics();
		try {
			logo.paint(graphics);
		}
		finally {
			graphics.dispose();
		}
		long visiblePixels = 0;
		for (int y = 0; y < rendered.getHeight(); y++) {
			for (int x = 0; x < rendered.getWidth(); x++) {
				if ((rendered.getRGB(x, y) >>> 24) != 0) {
					visiblePixels++;
				}
			}
		}
		assertThat(visiblePixels).isGreaterThan(1000);
	}

	private static void collectComponents(Container parent, List<JComponent> components) {
		for (Component child : parent.getComponents()) {
			if (child instanceof JComponent component) {
				components.add(component);
			}
			if (child instanceof Container container) {
				collectComponents(container, components);
			}
		}
	}

}
