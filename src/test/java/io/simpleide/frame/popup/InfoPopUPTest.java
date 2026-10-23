package io.simpleide.frame.popup;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

public class InfoPopUPTest {

    @Test
    public void createsPopupWithInfoMessageTypeAndIcon() {
        InfoPopUP popup = new InfoPopUP("Informação", "Arquivo salvo com sucesso", "Ok");

        assertThat(popup.getTitle()).isEqualTo("Informação");
        assertThat(popup.jp.getMessage()).isEqualTo("Arquivo salvo com sucesso");
        assertThat(popup.jp.getMessageType()).isEqualTo(JOptionPane.INFORMATION_MESSAGE);
        assertThat(popup.jp.getOptionType()).isEqualTo(JOptionPane.DEFAULT_OPTION);
        assertThat(popup.jp.getOptions()).containsExactly("Ok");
        assertThat(popup.jp.getInitialValue()).isEqualTo("Ok");
        assertThat(popup.jp.getIcon())
                .isInstanceOfSatisfying(ImageIcon.class, icon -> {
                    assertThat(icon.getIconWidth()).isPositive();
                    assertThat(icon.getIconHeight()).isPositive();
                });
    }

    @Test
    public void acceptsParentComponentInConstructor() {
        JComponent parent = new JPanel();

        InfoPopUP popup = new InfoPopUP("Informação", "Detalhes do processo", parent, "Ok");

        assertThat(popup.parent).isSameAs(parent);
        assertThat(popup.getTitle()).isEqualTo("Informação");
    }

    @Test
    public void mapsAndUpdatesTitle() {
        InfoPopUP popup = new InfoPopUP("Info", "Mensagem informativa", "Ok");

        assertThat(popup.getMap()).containsEntry("title", "Info");

        popup.setMap(Collections.<String, Object>singletonMap("title", "Atualizado"));

        assertThat(popup.getTitle()).isEqualTo("Atualizado");
    }

    @Test
    public void addsInfoIconToOptionPaneForRendering() {
        InfoPopUP popup = new InfoPopUP("Informação", "Mensagem informativa", "Ok");

        assertThat(containsIcon(popup.jp, popup.jp.getIcon())).isTrue();
    }

    private static boolean containsIcon(Container container, Icon expectedIcon) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel
                    && ((JLabel) component).getIcon() == expectedIcon) {
                return true;
            }
            if (component instanceof Container
                    && containsIcon((Container) component, expectedIcon)) {
                return true;
            }
        }
        return false;
    }
}
