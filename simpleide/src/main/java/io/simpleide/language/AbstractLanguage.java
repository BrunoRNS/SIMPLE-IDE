package io.simpleide.language;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.simpleide.language.exceptions.ProcessAlreadyExistsException;
import io.simpleide.language.exceptions.ProcessFatalException;
import io.simpleide.language.io.Output;
import io.simpleide.language.io.holder.InputHolder;
import io.simpleide.thread.ThreadMaster;
import io.simpleide.thread.nodes.runnable.RunnableNode;

/**
 * Base class for executable languages in Simple IDE.
 *
 * <p>
 * A language owns one or more {@link ProcessBuilder} steps and runs them in order. The
 * run stops at the first failed step, except that the final step always reaches the exit
 * handler. Output from the active process is published line by line to {@link Output},
 * while lines queued in {@link InputHolder} are forwarded to the process standard input
 * on a background {@link RunnableNode}.
 *
 * <p>
 * Subclasses configure {@link #pb} or {@link #processSteps} and may override
 * {@link #handleExit(Process)} or {@link #onExecutionFinished()} to customise the
 * lifecycle. See docs/api/languages.md for usage examples.
 *
 * <p>
 * Covered by {@code AbstractLanguageTest}.
 *
 * @see <a href=
 * "https://github.com/BrunoRNS/SIMPLE-IDE/blob/main/docs/api/languages.md">docs/api/languages.md</a>
 * @author BrunoRNS
 * @since v1.0.0beta
 */
public abstract class AbstractLanguage implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLanguage.class);

	protected ProcessBuilder pb;

	protected List<ProcessBuilder> processSteps = List.of();

	protected volatile Process p;

	protected volatile boolean stopped = false;

	private volatile RunnableNode inputForwarder;

	protected String langFriendly = null;

	protected String langConstant = null;

	/**
	 * Returns the human-readable language name configured by the subclass.
	 * @return the friendly language name, or {@code null} when not configured
	 */
	public String getLangFriendly() {
		return this.langFriendly;
	}

	/**
	 * Returns the syntax constant used to highlight files of this language.
	 * @return the language constant, or {@code null} when not configured
	 */
	public String getLangConstant() {
		return this.langConstant;
	}

	/**
	 * Executes the configured process pipeline and publishes its output.
	 * @throws ProcessAlreadyExistsException if this language already has a live process
	 * @throws ProcessFatalException if the process cannot start, fails, or the wait is
	 * interrupted
	 */
	@Override
	public void run() {
		List<ProcessBuilder> builders = getProcessBuilders();
		if (builders.isEmpty()) {
			LOGGER.error("[IDE] Cannot run language process because its command was not configured.");
			return;
		}

		synchronized (this) {
			if (this.p != null && this.p.isAlive()) {
				throw new ProcessAlreadyExistsException("Impossible to Run the script while still running another",
						this.p);
			}
		}

		stopped = false;
		try {
			for (int index = 0; index < builders.size(); index++) {
				Process process = builders.get(index).redirectErrorStream(true).start();
				p = process;
				if (index == builders.size() - 1) {
					startInputForwarder(process);
				}

				try (Scanner scanner = new Scanner(process.getInputStream())) {
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();
						Output.getInstance().set(line);
						LOGGER.info("[OUTPUT] {}", line);
					}
					IOException streamFailure = scanner.ioException();
					if (streamFailure != null) {
						throw streamFailure;
					}
				}

				int exitCode = process.waitFor();
				if (exitCode != 0 || index == builders.size() - 1) {
					getHandler().accept(process);
					return;
				}

				p = null;
			}
		}
		catch (IOException e) {
			if (stopped) {
				// The user stopped the process, which closes its output stream while the
				// reader thread may still be blocked on it. This is an expected shutdown,
				// not a process startup failure.
				LOGGER.info("[IDE] Process manually interrupted by the user.");
				finishExecution();
				return;
			}
			stopCurrentProcess();
			onExecutionFinished();
			throw new ProcessFatalException("Process Did not Finish Successfully at startup", e);
		}
		catch (InterruptedException e) {
			stopCurrentProcess();
			onExecutionFinished();
			Thread.currentThread().interrupt();
			throw new ProcessFatalException("Interrupted while waiting for the language process.", new IOException(e));
		}
	}

	protected List<ProcessBuilder> getProcessBuilders() {
		if (!processSteps.isEmpty()) {
			return processSteps;
		}
		return pb == null ? List.of() : List.of(pb);
	}

	private void stopCurrentProcess() {
		Process process = p;
		if (process != null && process.isAlive()) {
			process.destroyForcibly();
		}
		p = null;
		stopped = false;
	}

	private void startInputForwarder(Process process) {
		RunnableNode forwarder = new RunnableNode(() -> {
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
				while (process.isAlive()) {
					String line = InputHolder.INSTANCE.get(1);
					if (line != null) {
						writer.write(line);
						writer.newLine();
						writer.flush();
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (IOException e) {
				if (process.isAlive()) {
					LOGGER.debug("[IDE] Could not forward input to the language process.", e);
				}
			}
		});
		inputForwarder = forwarder;
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.initialize();
		threadMaster.register(forwarder);
	}

	/**
	 * Registers this language with the shared thread manager and starts it
	 * asynchronously.
	 * @return the runnable node that tracks the managed execution
	 */
	public final RunnableNode startManaged() {
		ThreadMaster threadMaster = ThreadMaster.getInstance();
		threadMaster.initialize();
		RunnableNode node = new RunnableNode(this);
		threadMaster.register(node);
		return node;
	}

	protected Consumer<Process> getHandler() {
		return new InternalExitHandler();
	}

	/**
	 * Adapter that forwards process completion to
	 * {@link AbstractLanguage#handleExit(Process)}.
	 *
	 * @author BrunoRNS
	 * @since v1.0.0beta
	 */
	protected final class InternalExitHandler implements Consumer<Process> {

		@Override
		public final void accept(Process t) {
			handleExit(t);
		}

	}

	protected void handleExit(Process t) {
		try {
			int exitCode = t.exitValue();

			if (exitCode != 0) {
				if (stopped) {
					LOGGER.info("[IDE] Process manually interrupted by the user.");
					return;
				}
				LOGGER.error("[OUTPUT] RUNTIME ERROR: {}", exitCode);
				LOGGER.error("[Runtime Error] The developer's program broke with code: {}", exitCode);
			}
			else {
				LOGGER.info("[IDE] Process completed successfully.");
			}
		}
		finally {
			finishExecution();
		}
	}

	private void finishExecution() {
		RunnableNode forwarder = inputForwarder;
		inputForwarder = null;
		if (forwarder != null) {
			ThreadMaster.getInstance().removeNodeCascade(forwarder);
		}
		this.p = null;
		this.stopped = false;
		onExecutionFinished();
	}

	protected void onExecutionFinished() {
	}

	/**
	 * Forcibly stops the current process and waits briefly for it to terminate.
	 * @throws ProcessFatalException if the process does not exit within three seconds
	 */
	public void forceStop() throws ProcessFatalException {
		Process process = this.p;
		if (process == null || !process.isAlive()) {
			return;
		}

		this.stopped = true;
		process.destroyForcibly();

		try {
			boolean killed = process.waitFor(3, TimeUnit.SECONDS);
			if (!killed) {
				throw new ProcessFatalException("The process refused to die within 3 seconds.", process);
			}
		}
		catch (InterruptedException e) {
			LOGGER.debug("[Debug] The interrupt thread was itself interrupted.", e);
			Thread.currentThread().interrupt();
		}
	}

}
