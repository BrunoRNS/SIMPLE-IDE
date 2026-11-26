package io.simpleide.ui.terminal;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable lifecycle state for one terminal tab.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
final class TerminalTab {

	final BigInteger id;

	final AtomicBoolean cleaned = new AtomicBoolean();

	final AtomicBoolean closeTaskStarted = new AtomicBoolean();

	final AtomicBoolean cleanupScheduled = new AtomicBoolean();

	private final AtomicReference<TerminalTabSnapshot> state = new AtomicReference<>(
			new TerminalTabSnapshot(null, null, false, false, false));

	TerminalTab(BigInteger id) {
		this.id = id;
	}

	boolean isLaunchComplete() {
		return state.get().launchComplete();
	}

	TerminalTabSnapshot snapshot() {
		return state.get();
	}

	void setSession(BusyBoxTerminalSession session) {
		state.updateAndGet(snapshot -> snapshot.withSession(session));
	}

	void setLaunchFailure(Throwable failure) {
		state.updateAndGet(snapshot -> snapshot.withLaunchFailure(failure));
	}

	void setLaunchSucceeded(boolean succeeded) {
		state.updateAndGet(snapshot -> snapshot.withLaunchSucceeded(succeeded));
	}

	void setLaunchComplete(boolean complete) {
		state.updateAndGet(snapshot -> snapshot.withLaunchComplete(complete));
	}

	void setCloseRequested(boolean requested) {
		state.updateAndGet(snapshot -> snapshot.withCloseRequested(requested));
	}

}

/**
 * @author BrunoRNS
 * @since v1.1.0
 */
record TerminalTabSnapshot(BusyBoxTerminalSession session, Throwable launchFailure, boolean launchSucceeded,
		boolean launchComplete, boolean closeRequested) {

	TerminalTabSnapshot withSession(BusyBoxTerminalSession session) {
		return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, launchComplete, closeRequested);
	}

	TerminalTabSnapshot withLaunchFailure(Throwable failure) {
		return new TerminalTabSnapshot(session, failure, launchSucceeded, launchComplete, closeRequested);
	}

	TerminalTabSnapshot withLaunchSucceeded(boolean succeeded) {
		return new TerminalTabSnapshot(session, launchFailure, succeeded, launchComplete, closeRequested);
	}

	TerminalTabSnapshot withLaunchComplete(boolean complete) {
		return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, complete, closeRequested);
	}

	TerminalTabSnapshot withCloseRequested(boolean requested) {
		return new TerminalTabSnapshot(session, launchFailure, launchSucceeded, launchComplete, requested);
	}

}
