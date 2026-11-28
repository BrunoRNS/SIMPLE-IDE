package io.simpleide.ui.menubar.menus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link io.simpleide.ui.menubar.menus.FileMenu}.
 */
class FileMenuTest {

	@Test
	void fileActionsInvokeTheirCallbacksOnTheEdt() throws Exception {
		CountDownLatch callbacksCompleted = new CountDownLatch(5);
		AtomicInteger callbacksInvoked = new AtomicInteger();
		AtomicBoolean callbacksOnEdt = new AtomicBoolean(true);
		Runnable callback = () -> {
			callbacksOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
			callbacksInvoked.incrementAndGet();
			callbacksCompleted.countDown();
		};

		SwingUtilities.invokeAndWait(() -> {
			FileMenu menu = new FileMenu(callback, callback, callback, callback, callback);
			assertThat(menu.getItemCount()).isEqualTo(5);
			assertThat(menu.getItem(0).getText()).isEqualTo("New File");
			assertThat(menu.getItem(1).getText()).isEqualTo("New Folder");
			assertThat(menu.getItem(2).getText()).isEqualTo("Open Folder");
			assertThat(menu.getItem(3).getText()).isEqualTo("Close Folder");
			assertThat(menu.getItem(4).getText()).isEqualTo("Save");
			for (int index = 0; index < menu.getItemCount(); index++) {
				menu.getItem(index).doClick();
			}
		});

		assertThat(callbacksCompleted.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(callbacksInvoked).hasValue(5);
		assertThat(callbacksOnEdt).isTrue();
	}

	@Test
	void rejectsMissingActionCallbacks() {
		assertThatThrownBy(() -> new FileMenu(null, () -> {
		}, () -> {
		}, () -> {
		}, () -> {
		})).isInstanceOf(NullPointerException.class).hasMessage("createFile");
	}

}
