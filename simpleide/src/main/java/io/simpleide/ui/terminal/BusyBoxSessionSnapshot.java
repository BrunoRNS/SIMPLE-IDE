package io.simpleide.ui.terminal;

import com.pty4j.PtyProcess;

import io.simpleide.ui.terminal.core.busybox.BusyBoxExecutable;

/**
 * Immutable snapshot of a BusyBox terminal session's process resources.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
record BusyBoxSessionSnapshot(BusyBoxExecutable executable, PtyProcess process, BusyBoxTtyConnector connector,
		boolean systemShellFallback, boolean x86Compatibility) {

	BusyBoxSessionSnapshot withExecutable(BusyBoxExecutable value) {
		return new BusyBoxSessionSnapshot(value, process, connector, systemShellFallback, x86Compatibility);
	}

	BusyBoxSessionSnapshot withProcess(PtyProcess value) {
		return new BusyBoxSessionSnapshot(executable, value, connector, systemShellFallback, x86Compatibility);
	}

	BusyBoxSessionSnapshot withConnector(BusyBoxTtyConnector value) {
		return new BusyBoxSessionSnapshot(executable, process, value, systemShellFallback, x86Compatibility);
	}

	BusyBoxSessionSnapshot withSystemShellFallback(boolean value) {
		return new BusyBoxSessionSnapshot(executable, process, connector, value, x86Compatibility);
	}

	BusyBoxSessionSnapshot withX86Compatibility(boolean value) {
		return new BusyBoxSessionSnapshot(executable, process, connector, systemShellFallback, value);
	}

}
