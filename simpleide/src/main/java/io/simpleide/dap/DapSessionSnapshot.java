package io.simpleide.dap;

import java.util.List;

/**
 * Immutable snapshot of a debug session's adapter and execution state.
 *
 * @author BrunoRNS
 * @since v1.1.0
 */
record DapSessionSnapshot(int adapterPort, List<Integer> breakpoints, int threadId, boolean stopped, boolean closed,
		boolean protocolStarted) {

	static DapSessionSnapshot initial() {
		return new DapSessionSnapshot(0, List.of(), DapSession.UNKNOWN_THREAD, false, false, false);
	}

	DapSessionSnapshot withAdapterPort(int value) {
		return new DapSessionSnapshot(value, breakpoints, threadId, stopped, closed, protocolStarted);
	}

	DapSessionSnapshot withBreakpoints(List<Integer> value) {
		return new DapSessionSnapshot(adapterPort, value, threadId, stopped, closed, protocolStarted);
	}

	DapSessionSnapshot withThreadId(int value) {
		return new DapSessionSnapshot(adapterPort, breakpoints, value, stopped, closed, protocolStarted);
	}

	DapSessionSnapshot withStopped(boolean value) {
		return new DapSessionSnapshot(adapterPort, breakpoints, threadId, value, closed, protocolStarted);
	}

	DapSessionSnapshot withClosed(boolean value) {
		return new DapSessionSnapshot(adapterPort, breakpoints, threadId, stopped, value, protocolStarted);
	}

	DapSessionSnapshot withProtocolStarted(boolean value) {
		return new DapSessionSnapshot(adapterPort, breakpoints, threadId, stopped, closed, value);
	}

}
