package io.simpleide.ui.terminal;

import java.nio.charset.Charset;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

/**
 * JediTerm connector for a BusyBox PTY process.
 *
 * @author BrunoRNS
 * @since v1.0.0beta
 */
final class BusyBoxTtyConnector extends ProcessTtyConnector {

	private final PtyProcess process;

	private final String name;

	BusyBoxTtyConnector(PtyProcess process, Charset charset, String name) {
		super(process, charset);
		this.process = process;
		this.name = name;
	}

	@Override
	public void resize(TermSize termSize) {
		if (isConnected()) {
			process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
		}
	}

	@Override
	public String getName() {
		return name;
	}

}
